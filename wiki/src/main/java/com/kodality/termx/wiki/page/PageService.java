package com.kodality.termx.wiki.page;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import com.kodality.termx.wiki.pagelink.PageLinkService;
import com.kodality.termx.wiki.pagerelation.PageRelationService;
import com.kodality.termx.wiki.pagetag.PageTagService;
import java.util.Optional;
import java.util.UUID;
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
    page = save(page);
    pageContentService.save(content, page.getId());
    return page;
  }

  @Transactional
  public Page save(Page page) {
    if (page.getCode() == null) {
      page.setCode(UUID.randomUUID().toString());
    }
    repository.save(page);
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
