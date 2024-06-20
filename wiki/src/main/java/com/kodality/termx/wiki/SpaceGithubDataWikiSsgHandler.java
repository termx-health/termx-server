package com.kodality.termx.wiki;


import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.core.github.GithubService.GithubContent.GithubContentEncoding;
import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.core.sys.space.SpaceService;
import com.kodality.termx.sys.space.Space;
import com.kodality.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageAttachment;
import com.kodality.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageContent;
import com.kodality.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageRelatedResource;
import com.kodality.termx.wiki.page.Page;
import com.kodality.termx.wiki.page.Page.PageAttachment;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageLink;
import com.kodality.termx.wiki.page.PageLinkQueryParams;
import com.kodality.termx.wiki.page.PageQueryParams;
import com.kodality.termx.wiki.page.PageService;
import com.kodality.termx.wiki.pageattachment.PageAttachmentService;
import com.kodality.termx.wiki.pagelink.PageLinkService;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.server.types.files.StreamedFile;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;

import static com.kodality.commons.util.JsonUtil.toPrettyJson;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataWikiSsgHandler implements SpaceGithubDataHandler {
  private final SpaceService spaceService;
  private final PageService pageService;
  private final PageAttachmentService pageAttachmentService;
  private final PageLinkService pageLinkService;
  private final List<WikiPageRelatedResourceProvider> resourceProviders;

  @Value("${termx.web-url}")
  private Optional<String> termxWebUrl;

  @Override
  public String getName() {
    return "wiki-ssg";
  }

  @Override
  public String getDefaultDir() {
    return "__source";
  }

  @Override
  public List<ResourceContent> getContent(Long spaceId) {
    Space space = spaceService.load(spaceId);
    List<Page> pages = pageService.query(new PageQueryParams().setSpaceIds(spaceId.toString()).all()).getData();
    List<PageLink> links = pageLinkService.query(new PageLinkQueryParams().setSpaceIds(spaceId.toString()).all()).getData();

    List<PageContent> contents = pages.stream().flatMap(p -> p.getContents().stream()).toList();
    List<SpaceGithubPageAttachment> attachments = pages.stream().flatMap(p -> {
      List<PageAttachment> atts = pageAttachmentService.getAttachments(p.getId());
      return atts.stream().map(att -> {
        try {
          StreamedFile file = pageAttachmentService.getAttachmentContent(p.getId(), att.getFileName());
          byte[] bytes = IOUtils.toByteArray(file.getInputStream());
          return new SpaceGithubPageAttachment(p.getId(), att.getFileName(), Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }).toList();

    List<SpaceGithubPageRelatedResource> relatedResources = pages.stream()
        .flatMap(p -> p.getRelations().stream())
        .filter(distinctByKey(rel -> rel.getType() + "#" + rel.getTarget()))
        .map(rel -> {
          return resourceProviders.stream()
              .filter(rp -> rp.getRelationType().equals(rel.getType())).findFirst()
              .flatMap(rp -> {
                return rp.getContent(rel.getTarget()).map(c -> new SpaceGithubPageRelatedResource(rp.gerResourceName(), rel.getTarget(), c, "json"));
              });
        })
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();

    List<ResourceContent> result = new ArrayList<>();
    result.add(new ResourceContent("space.json", toPrettyJson(new SsgSpaceIndex(termxWebUrl.orElse(null), space.getCode(), space.getNames()))));
    result.add(new ResourceContent("pages.json", toPrettyJson(composePagesIndex(pages, links))));
    result.addAll(contents.stream().map(p -> new ResourceContent(
        "pages/" + p.getSlug() + (p.getContentType().equals("html") ? ".html" : ".md"),
        p.getContent()
    )).toList());
    result.addAll(attachments.stream().map(a -> new ResourceContent(
        "attachments/" + a.pageId() + "/" + a.name(),
        a.base64(),
        GithubContentEncoding.base64
    )).toList());
    result.addAll(relatedResources.stream().map(r -> new ResourceContent(
        "resources/" + r.resourceType() + "/" + r.name() + "." + r.contentType(),
        r.content
    )).toList());
    return result;
  }


  private List<SpaceGithubPage> composePagesIndex(List<Page> pages, List<PageLink> links) {
    Map<Long, PageLink> pageLinks = links.stream().collect(Collectors.toMap(PageLink::getTargetId, l -> l));

    Map<Long, List<Page>> pagesTree = pages.stream()
        .filter(p -> pageLinks.get(p.getId()) != null)
        .sorted(Comparator.comparing(Page::getId))
        .sorted(Comparator.comparing(p -> pageLinks.get(p.getId()).getOrderNumber()))
        .collect(Collectors.groupingBy(p -> CollectionUtils.isEmpty(p.getLinks()) ? 0L : p.getLinks().get(0).getSourceId()));

    return pagesTree.isEmpty() ? List.of() : buildPages(0L, pagesTree);
  }

  private List<SpaceGithubPage> buildPages(Long parent, Map<Long, List<Page>> allPages) {
    return !allPages.containsKey(parent) ? null : allPages.get(parent).stream().map(p -> {
      return new SpaceGithubPage(
          p.getCode(),
          p.getContents().stream()
              .sorted(Comparator.comparing(PageContent::getSlug))
              .map(c -> new SpaceGithubPageContent(c.getName(), c.getSlug(), c.getLang(), c.getContentType(), c.getModifiedAt()))
              .toList(),
          buildPages(p.getId(), allPages)
      );
    }).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    // intentionally left blank
  }

  protected record SpaceGithubPage(String code, List<SpaceGithubPageContent> contents, List<SpaceGithubPage> children) {
    protected record SpaceGithubPageContent(String name, String slug, String lang, String contentType, OffsetDateTime modifiedAt) {}

    protected record SpaceGithubPageAttachment(Long pageId, String name, String base64) {}

    protected record SpaceGithubPageRelatedResource(String resourceType, String name, String content, String contentType) {}
  }

  protected record SsgSpaceIndex(String web, String code, LocalizedName names) {}

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}
