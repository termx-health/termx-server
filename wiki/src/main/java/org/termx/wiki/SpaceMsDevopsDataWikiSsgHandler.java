package org.termx.wiki;


import org.termx.core.github.GithubService.GithubContent.GithubContentEncoding;
import org.termx.core.github.ResourceContentProvider.ResourceContent;
import org.termx.core.msdevops.SpaceMsDevopsDataHandler;
import org.termx.core.sys.space.SpaceService;
import org.termx.sys.space.Space;
import org.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageAttachment;
import org.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageContent;
import org.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageRelatedResource;
import org.termx.wiki.page.*;
import org.termx.wiki.page.Page.PageAttachment;
import org.termx.wiki.pageattachment.PageAttachmentService;
import org.termx.wiki.pagelink.PageLinkService;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.server.types.files.StreamedFile;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.kodality.commons.util.JsonUtil.toPrettyJson;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceMsDevopsDataWikiSsgHandler implements SpaceMsDevopsDataHandler {
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
    result.add(new ResourceContent("space.json", toPrettyJson(new SpaceGithubDataWikiSsgHandler.SsgSpaceIndex(
        termxWebUrl.orElse(null), space.getCode(), space.getNames(),
        space.getDescription(), space.getDefaultLanguage(), space.getLanguages(), space.getSiteUrl(),
        SpaceGithubDataWikiSsgHandler.ssgConfig(space)))));
    result.add(new ResourceContent("pages.json", toPrettyJson(composePagesIndex(pages, links))));
    result.addAll(contents.stream().map(p -> {
      boolean html = "html".equals(p.getContentType());
      String body = html ? p.getContent() : SpaceGithubDataWikiSsgHandler.ensureH1(p.getContent(), p.getName());
      return new ResourceContent("pages/" + p.getSlug() + (html ? ".html" : ".md"), body);
    }).toList());
    result.addAll(attachments.stream().map(a -> new ResourceContent(
        "attachments/" + a.pageId() + "/" + a.name(),
        a.base64(),
        GithubContentEncoding.base64
    )).toList());
    result.addAll(relatedResources.stream().map(r -> new ResourceContent(
        "resources/" + r.resourceType() + "/" + r.name() + "." + r.contentType(),
        r.content()
    )).toList());
    return result;
  }


  private List<SpaceGithubDataWikiSsgHandler.SpaceGithubPage> composePagesIndex(List<Page> pages, List<PageLink> links) {
    Map<Long, PageLink> pageLinks = links.stream().collect(Collectors.toMap(PageLink::getTargetId, l -> l));

    Map<Long, List<Page>> pagesTree = pages.stream()
        .filter(p -> pageLinks.get(p.getId()) != null)
        .sorted(Comparator.comparing(Page::getId))
        .sorted(Comparator.comparing(p -> pageLinks.get(p.getId()).getOrderNumber()))
        .collect(Collectors.groupingBy(p -> CollectionUtils.isEmpty(p.getLinks()) ? 0L : p.getLinks().get(0).getSourceId()));

    return pagesTree.isEmpty() ? List.of() : buildPages(0L, pagesTree);
  }

  private List<SpaceGithubDataWikiSsgHandler.SpaceGithubPage> buildPages(Long parent, Map<Long, List<Page>> allPages) {
    return !allPages.containsKey(parent) ? null : allPages.get(parent).stream().map(p -> {
      return new SpaceGithubDataWikiSsgHandler.SpaceGithubPage(
          p.getCode(),
          p.getContents().stream()
              .sorted(Comparator.comparing(PageContent::getSlug))
              .map(c -> new SpaceGithubPageContent(c.getName(), c.getSlug(), c.getLang(), c.getContentType(),
                  c.getDescription(), c.getModifiedAt()))
              .toList(),
          buildPages(p.getId(), allPages),
          SpaceGithubDataWikiSsgHandler.pageTags(p)
      );
    }).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    // intentionally left blank
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}
