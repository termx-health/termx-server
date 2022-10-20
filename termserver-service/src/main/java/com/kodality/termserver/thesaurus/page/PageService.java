package com.kodality.termserver.thesaurus.page;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.thesaurus.pagecontent.PageContentService;
import com.kodality.termserver.thesaurus.pagelink.PageLinkService;
import com.kodality.termserver.thesaurus.pagetag.PageTagService;
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
    pageLinkService.save(page.getLinks(), page.getId());
    pageTagService.save(page.getTags(), page.getId());
    return load(page.getId()).orElse(null);
  }

  @Transactional
  public void cancel(Long id) {
    repository.cancel(id);
  }

  private Page decorate(Page page) {
    page.setContents(pageContentService.loadAll(page.getId()));
    page.setLinks(pageLinkService.loadAll(page.getId()));
    page.setTags(pageTagService.loadAll(page.getId()));
    return page;
  }
}
