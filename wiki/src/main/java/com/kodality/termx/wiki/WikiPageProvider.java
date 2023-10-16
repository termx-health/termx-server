package com.kodality.termx.wiki;


import com.kodality.termx.core.wiki.PageProvider;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageContentQueryParams;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import java.util.List;
import javax.inject.Singleton;
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
