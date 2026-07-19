package org.termx.wiki;


import com.kodality.commons.model.LocalizedName;
import org.termx.core.github.GithubService.GithubContent.GithubContentEncoding;
import org.termx.core.github.ResourceContentProvider.ResourceContent;
import org.termx.core.sys.space.SpaceGithubDataHandler;
import org.termx.core.sys.space.SpaceService;
import org.termx.sys.space.Space;
import org.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageAttachment;
import org.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageContent;
import org.termx.wiki.SpaceGithubDataWikiSsgHandler.SpaceGithubPage.SpaceGithubPageRelatedResource;
import org.termx.wiki.page.Page;
import org.termx.wiki.page.Page.PageAttachment;
import org.termx.wiki.page.PageContent;
import org.termx.wiki.page.PageLink;
import org.termx.wiki.page.PageLinkQueryParams;
import org.termx.wiki.page.PageQueryParams;
import org.termx.wiki.page.PageService;
import org.termx.wiki.pageattachment.PageAttachmentService;
import org.termx.wiki.pagelink.PageLinkService;
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
import java.util.regex.Pattern;
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
    result.add(new ResourceContent("space.json", toPrettyJson(new SsgSpaceIndex(
        termxWebUrl.orElse(null), space.getCode(), space.getNames(),
        space.getDescription(), space.getDefaultLanguage(), space.getLanguages(), space.getSiteUrl(),
        ssgConfig(space)))));
    result.add(new ResourceContent("pages.json", toPrettyJson(composePagesIndex(pages, links))));
    result.addAll(contents.stream().map(p -> {
      boolean html = "html".equals(p.getContentType());
      // §A: materialize the page name as the file's H1 so the exported markdown is
      // self-describing (the title otherwise lives only in pages.json). Skip HTML content
      // and pages whose body already opens with an H1.
      String body = html ? p.getContent() : ensureH1(p.getContent(), p.getName());
      return new ResourceContent("pages/" + p.getSlug() + (html ? ".html" : ".md"), body);
    }).toList());
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
              .map(c -> new SpaceGithubPageContent(c.getName(), c.getSlug(), c.getLang(), c.getContentType(),
                  c.getDescription(), c.getModifiedAt()))
              .toList(),
          buildPages(p.getId(), allPages),
          pageTags(p)
      );
    }).toList();
  }

  // Page-level tags, exported so the generator can surface them (e.g. as <meta keywords>).
  static List<String> pageTags(Page p) {
    return CollectionUtils.isEmpty(p.getTags()) ? null : p.getTags().stream()
        .map(t -> t.getTag() == null ? null : t.getTag().getText())
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    // intentionally left blank
  }

  protected record SpaceGithubPage(String code, List<SpaceGithubPageContent> contents, List<SpaceGithubPage> children,
                                   List<String> tags) {
    protected record SpaceGithubPageContent(String name, String slug, String lang, String contentType,
                                            String description, OffsetDateTime modifiedAt) {}

    protected record SpaceGithubPageAttachment(Long pageId, String name, String base64) {}

    protected record SpaceGithubPageRelatedResource(String resourceType, String name, String content, String contentType) {}
  }

  protected record SsgSpaceIndex(String web, String code, LocalizedName names,
                                 LocalizedName description, String defaultLang, List<String> langs, String siteUrl,
                                 SsgConfig ssg) {}

  // Static-site generator config, shaped to mirror mdbook's config.yml so the generator can merge it
  // directly (a repo's own .mdbook/config.yml still overrides). Any all-null group is omitted.
  protected record SsgConfig(SsgTheme theme, SsgFooter footer, String txServer, Boolean search, String logo) {}
  protected record SsgTheme(String skin, String accent, Boolean switcher) {}
  protected record SsgFooter(String message, String copyright) {}

  static SsgConfig ssgConfig(Space space) {
    SsgTheme theme = anyNonNull(space.getSsgSkin(), space.getSsgThemeAccent(), space.getSsgThemeSwitcher())
        ? new SsgTheme(space.getSsgSkin(), space.getSsgThemeAccent(), space.getSsgThemeSwitcher()) : null;
    SsgFooter footer = anyNonNull(space.getSsgFooterMessage(), space.getSsgFooterCopyright())
        ? new SsgFooter(space.getSsgFooterMessage(), space.getSsgFooterCopyright()) : null;
    if (!anyNonNull(theme, footer, space.getSsgTxServer(), space.getSsgSearch(), space.getSsgLogo())) {
      return null;
    }
    return new SsgConfig(theme, footer, space.getSsgTxServer(), space.getSsgSearch(), space.getSsgLogo());
  }

  private static boolean anyNonNull(Object... values) {
    for (Object v : values) {
      if (v != null) {
        return true;
      }
    }
    return false;
  }

  // Prepend "# {name}" as the body's H1 unless it already opens with one (a single leading '#').
  private static final Pattern LEADING_H1 = Pattern.compile("^\\s*#(?!#)\\s");

  static String ensureH1(String content, String name) {
    String body = content == null ? "" : content;
    if (name == null || name.isBlank() || LEADING_H1.matcher(body).find()) {
      return body;
    }
    return "# " + name + "\n\n" + body;
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}
