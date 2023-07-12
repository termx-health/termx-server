package com.kodality.termx.thesaurus.page;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.thesaurus.pagecontent.PageContentService;
import com.kodality.termx.thesaurus.pagelink.PageLinkService;
import com.kodality.termx.thesaurus.pagerelation.PageRelationService;
import com.kodality.termx.thesaurus.pagetag.PageTagService;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageService {
  private final PageRepository repository;
  private final PageTagService pageTagService;
  private final PageLinkService pageLinkService;
  private final PageContentService pageContentService;
  private final PageRelationService pageRelationService;

  public Optional<Page> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(this::decorate);
  }

  public QueryResult<Page> query(PageQueryParams params) {
    QueryResult<Page> pages = repository.query(params);
    pages.getData().forEach(this::decorate);
    return pages;
  }

  @Transactional
  public Page save(Page page, PageContent content) {
    repository.save(page);
    pageContentService.save(content, page.getId());
    pageLinkService.saveSources(page.getLinks(), page.getId());
    pageTagService.save(page.getTags(), page.getId());
    return load(page.getId()).orElse(null);
  }

  @Transactional
  public void cancel(Long id) {
    repository.cancel(id);
  }

  private Page decorate(Page page) {
    page.setContents(pageContentService.loadAll(page.getId()));
    page.setLinks(pageLinkService.loadSources(page.getId()));
    page.setTags(pageTagService.loadAll(page.getId()));
    page.setRelations(pageRelationService.loadAll(page.getId()));
    return page;
  }
}