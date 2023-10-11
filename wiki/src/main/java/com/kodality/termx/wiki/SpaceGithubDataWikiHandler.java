package com.kodality.termx.wiki;


import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.wiki.SpaceGithubDataWikiHandler.SpaceGithubPage.SpaceGithubPageContent;
import com.kodality.termx.wiki.page.Page;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageLink;
import com.kodality.termx.wiki.page.PageLinkQueryParams;
import com.kodality.termx.wiki.page.PageQueryParams;
import com.kodality.termx.wiki.page.PageService;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import com.kodality.termx.wiki.pagelink.PageLinkService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataWikiHandler implements SpaceGithubDataHandler {
  private final PageContentService pageContentService;
  private final PageService pageService;
  private final PageLinkService pageLinkService;

  @Override
  public String getName() {
    return "wiki";
  }

  @Override
  public String getDefaultDir() {
    return "input/pagecontent";
  }

  @Override
  public Map<String, String> getContent(Long spaceId) {
    Map<Long, PageLink> links = pageLinkService.query(new PageLinkQueryParams().setSpaceIds(spaceId.toString()).all()).getData().stream()
        .collect(Collectors.toMap(PageLink::getTargetId, l -> l));
    List<Page> pages = pageService.query(new PageQueryParams().setSpaceIds(spaceId.toString()).all()).getData();
    Map<Long, List<Page>> pagesTree = pages.stream()
        .sorted(Comparator.comparing(Page::getId))
        .sorted(Comparator.comparing(p -> links.get(p.getId()).getOrderNumber()))
        .collect(Collectors.groupingBy(p -> CollectionUtils.isEmpty(p.getLinks()) ? 0L : p.getLinks().get(0).getSourceId()));
    Map<String, String> result = new HashMap<>();
    result.put("pages.json", JsonUtil.toPrettyJson(pagesTree.isEmpty() ? List.of() : buildPages(0L, pagesTree)));
    result.putAll(pages.stream().flatMap(p -> p.getContents().stream()).collect(Collectors.toMap(p -> p.getSlug() + ".md", PageContent::getContent)));
    return result;
  }

  private List<SpaceGithubPage> buildPages(Long parent, Map<Long, List<Page>> allPages) {
    return !allPages.containsKey(parent) ? null : allPages.get(parent).stream().map(p -> {
      return new SpaceGithubPage(
          p.getCode(),
          p.getContents().stream().sorted(Comparator.comparing(PageContent::getSlug))
              .map(c -> new SpaceGithubPageContent(c.getName(), c.getSlug(), c.getLang(), c.getContentType())).toList(),
          buildPages(p.getId(), allPages)
      );
    }).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    Map<String, Page> currentPages = pageService.query(new PageQueryParams().setSpaceIds(spaceId.toString()).all()).getData()
        .stream().collect(Collectors.toMap(p -> p.getCode(), p -> p));
    Map<String, PageContent> currentContents = currentPages.values().stream().flatMap(p -> p.getContents()
        .stream()).collect(Collectors.toMap(c -> c.getSlug(), c -> c));

    if (content.containsKey("pages.json")) {
      List<SpaceGithubPage> pages = JsonUtil.fromJson(content.get("pages.json"), JsonUtil.getListType(SpaceGithubPage.class));
      List<String> newSlugs = collectSlugs(pages).toList();
      List<String> newPageCodes = collectPageCodes(pages).toList();
      currentContents.keySet().stream().filter(s -> !newSlugs.contains(s)).toList().forEach(s -> {
        log.info("deleting content " + s);
        pageContentService.delete(currentContents.get(s).getId());
        currentContents.remove(s);
      });
      currentPages.keySet().stream().filter(s -> !newPageCodes.contains(s)).toList().forEach(s -> {
        log.info("deleting page " + s);
        pageService.cancel(currentPages.get(s).getId());
        currentPages.remove(s);
      });
      saveTree(spaceId, null, pages, currentPages, currentContents);
      content.remove("pages.json");
    }

    content.forEach((f, c) -> {
      if (c == null) {
        return; //delete, but should be already deleted.
      }
      String slug = StringUtils.removeEnd(f, ".md");
      PageContent pc = currentContents.get(slug);
      pc.setContent(c);
      pageContentService.save(pc, pc.getPageId());
    });
  }

  private Stream<String> collectSlugs(List<SpaceGithubPage> pages) {
    return pages.stream().flatMap(p -> Stream.concat(
        p.contents.stream().map(x -> x.slug),
        p.children == null ? Stream.of() : collectSlugs(p.children)
    ));
  }

  private Stream<String> collectPageCodes(List<SpaceGithubPage> pages) {
    return pages.stream().flatMap(p -> Stream.concat(
        Stream.of(p.code),
        p.children == null ? Stream.of() : collectPageCodes(p.children)
    ));
  }

  private void saveTree(Long spaceId, Long parentPageId, List<SpaceGithubPage> pages,
                        Map<String, Page> currentPages, Map<String, PageContent> currentContents) {
    pages.forEach(p -> {
      Page page = currentPages.containsKey(p.code) ? currentPages.get(p.code) : new Page().setCode(p.code).setSpaceId(spaceId).setStatus("draft");
      Integer order = pages.indexOf(p) + 1;
      if (page.getId() != null) {
        List<PageLink> links = pageLinkService.query(new PageLinkQueryParams().setTargetIds(page.getId().toString())).getData();
        if (!links.get(0).getOrderNumber().equals(order)) {
          log.info("saving order for " + page.getCode() + " " + page.getContents().get(0).getSlug() + " " + links.get(0).getOrderNumber() + " -> " + order);
          links.get(0).setOrderNumber(order);
          pageLinkService.saveSources(links, page.getId());
        }
      }
      if (page.getId() == null || !Objects.equals(parentPageId, (CollectionUtils.isEmpty(page.getLinks()) ? null : page.getLinks().get(0).getSourceId()))) {
        page.setLinks(parentPageId == null ? new ArrayList<>(/* how to set order number? */)
            : List.of(new PageLink().setSourceId(parentPageId).setOrderNumber(order)));
        log.info("saving " + page.getCode());
        page = pageService.save(page);
        currentPages.put(page.getCode(), page);
      }
      Long pageId = currentPages.get(p.code).getId();
      if (p.contents != null) {
        p.contents.forEach(c -> {
          PageContent cc = currentContents.containsKey(c.slug) ? currentContents.get(c.slug) : new PageContent().setSlug(c.slug);
          if (!Objects.equals(cc.getPageId(), pageId) || !Objects.equals(cc.getLang(), c.lang) || !Objects.equals(cc.getName(), c.name) ||
              !Objects.equals(cc.getContentType(), c.ct)) {
            cc.setPageId(pageId);
            cc.setLang(c.lang);
            cc.setName(c.name);
            cc.setContentType(c.ct);
            log.info("saving " + cc.getSlug());
            pageContentService.save(cc, pageId);
            currentContents.put(cc.getSlug(), cc);
          }
        });
      }
      if (p.children != null) {
        saveTree(spaceId, pageId, p.children, currentPages, currentContents);
      }
    });
  }

  public record SpaceGithubPage(String code, List<SpaceGithubPageContent> contents, List<SpaceGithubPage> children) {
    public record SpaceGithubPageContent(String name, String slug, String lang, String ct) {}
  }
}
