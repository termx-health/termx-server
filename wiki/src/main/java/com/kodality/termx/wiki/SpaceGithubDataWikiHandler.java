package com.kodality.termx.wiki;


import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.wiki.SpaceGithubDataWikiHandler.SpaceGithubPage.SpaceGithubPageContent;
import com.kodality.termx.wiki.page.Page;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageContentQueryParams;
import com.kodality.termx.wiki.page.PageQueryParams;
import com.kodality.termx.wiki.page.PageService;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataWikiHandler implements SpaceGithubDataHandler {
  private final PageContentService pageContentService;
  private final PageService pageService;

  @Override
  public String getName() {
    return "wiki";
  }

  @Override
  public Map<String, String> getContent(Long spaceId) {
    List<Page> pages = pageService.query(new PageQueryParams().setSpaceIds(spaceId.toString()).all()).getData();
    Map<Long, List<Page>> pagesTree = pages.stream()
        .collect(Collectors.groupingBy(p -> CollectionUtils.isEmpty(p.getLinks()) ? 0L : p.getLinks().get(0).getSourceId()));
    Map<String, String> result = new HashMap<>();
    result.put("pages.json", JsonUtil.toJson(buildPages(0L, pagesTree)));
    result.putAll(pages.stream().flatMap(p -> p.getContents().stream()).collect(Collectors.toMap(p -> p.getSlug() + ".md", PageContent::getContent)));
    return result;
  }

  private List<SpaceGithubPage> buildPages(Long parent, Map<Long, List<Page>> allPages) {
    return !allPages.containsKey(parent) ? null : allPages.get(parent).stream().map(p -> {
      return new SpaceGithubPage(
          p.getContents().stream().map(c -> new SpaceGithubPageContent(c.getName(), c.getSlug(), c.getLang(), c.getContentType())).toList(),
          buildPages(p.getId(), allPages)
      );
    }).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> content) {
    content.forEach((f, c) -> {
      if (f.equals("pages.json")) {
        return;//TODO
      }
      String slug = StringUtils.removeEnd(f, ".md");
      if (c == null) {
        return; //TODO delete
      }
      QueryResult<PageContent> qr = pageContentService.query(new PageContentQueryParams().setSpaceIds(spaceId.toString()).setSlugs(slug).limit(1));
      if (qr.getMeta().getTotal() == 0) {
        //TODO create
        return;
      }
      if (qr.getMeta().getTotal() > 1) {
        throw new IllegalStateException("was expecting 0 or 1 page contents");
      }
      PageContent pc = qr.findFirst().orElseThrow();
      pc.setContent(c);
      pageContentService.save(pc, pc.getPageId());
    });
  }

  public record SpaceGithubPage(List<SpaceGithubPageContent> contents, List<SpaceGithubPage> children) {
    public record SpaceGithubPageContent(String name, String slug, String lang, String ct) {}
  }
}
