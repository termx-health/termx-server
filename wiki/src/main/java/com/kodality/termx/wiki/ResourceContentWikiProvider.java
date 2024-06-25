package com.kodality.termx.wiki;


import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.wiki.page.Page;
import com.kodality.termx.wiki.page.PageQueryParams;
import com.kodality.termx.wiki.page.PageService;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ResourceContentWikiProvider implements ResourceContentProvider {
  private final PageService pageService;

  @Override
  public String getResourceType() {
    return "WikiPage";
  }

  @Override
  public String getContentType() {
    return "md";
  }

  @Override
  public List<ResourceContent> getContent(String spacePagePipe) {
    String[] pipe = PipeUtil.parsePipe(spacePagePipe);
    Page page = pageService.query(new PageQueryParams().setSpaceIds(pipe[0]).setSlugs(pipe[1]).limit(1)).findFirst()
        .orElseThrow(() -> new NotFoundException("Page not found: " + spacePagePipe));
    return page.getContents().stream().map(pc -> new ResourceContent(pc.getSlug() + ".md", pc.getContent())).toList();
  }
}
