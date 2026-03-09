package org.termx.wiki;


import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.PipeUtil;
import org.termx.core.github.ResourceContentProvider;
import org.termx.wiki.page.Page;
import org.termx.wiki.page.PageQueryParams;
import org.termx.wiki.page.PageService;
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
