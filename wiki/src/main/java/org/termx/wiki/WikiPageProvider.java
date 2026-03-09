package org.termx.wiki;


import org.termx.core.wiki.PageProvider;
import org.termx.wiki.page.PageContent;
import org.termx.wiki.page.PageContentQueryParams;
import org.termx.wiki.pagecontent.PageContentService;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class WikiPageProvider extends PageProvider {
  private final PageContentService pageContentService;

  @Override
  public List<PageContent> getRelatedPageContents(String resourceId, String resourceType) {
    return pageContentService.query(new PageContentQueryParams().setRelations(resourceType + "|" + resourceId).all()).getData();
  }

  @Override
  public PageContent load(Long id) {
    return pageContentService.load(id);
  }
}
