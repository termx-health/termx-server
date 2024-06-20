package com.kodality.termx.wiki.page;

import com.kodality.commons.model.QueryResult;
import com.kodality.commons.stream.Collectors;
import com.kodality.termx.wiki.ApiError;
import com.kodality.termx.wiki.pagecomment.PageCommentService;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import com.kodality.termx.wiki.pagelink.PageLinkService;
import com.kodality.termx.wiki.pagerelation.PageRelationService;
import com.kodality.termx.wiki.pagetag.PageTagService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageService {
  private final PageRepository repository;
  private final PageTagService pageTagService;
  private final PageLinkService pageLinkService;
  private final PageContentService pageContentService;
  private final PageRelationService pageRelationService;
  private final PageCommentService pageCommentService;

  public Optional<Page> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(this::decorate);
  }

  public QueryResult<Page> query(PageQueryParams params) {
    QueryResult<Page> pages = repository.query(params);
    pages.getData().forEach(this::decorate);
    return pages;
  }

  public List<PageTreeItem> loadTree(Long spaceId) {
    List<PageTreeItem> flat = repository.loadTree(spaceId);
    Map<Long, List<PageTreeItem>> map = flat.stream().collect(Collectors.groupingBy(PageTreeItem::getParentPageId));
    flat.forEach(i -> i.setChildren(map.get(i.getPageId())));
    return flat.stream().filter(i -> i.getParentPageId() == null).toList();
  }

  @Transactional
  public Page save(Page page, PageContent content) {
    page = save(page);
    pageContentService.save(content, page.getId());
    return load(page.getId()).orElse(null);
  }

  @Transactional
  public Page save(Page page) {
    if (page.getCode() == null) {
      page.setCode(UUID.randomUUID().toString());
    }
    if (page.getId() != null) {
      Page current = repository.load(page.getId());
      if (!current.getSpaceId().equals(page.getSpaceId())) {
        throw ApiError.T001.toApiException();
      }
    }
    repository.save(page);
    pageLinkService.saveSources(page.getLinks(), page.getId());
    pageTagService.save(page.getTags(), page.getId());
    return load(page.getId()).orElse(null);
  }

  @Transactional
  public void delete(Long pageId) {
    pageLinkService.loadDescendants(pageId).forEach(l -> {
      Long id = l.getTargetId();

      List<PageLink> sourceLinks = pageLinkService.loadSources(id);
      List<Long> linkIdsToClose = ListUtils.union(sourceLinks.stream().map(PageLink::getId).toList(), List.of(l.getId()));
      pageLinkService.closeLinks(linkIdsToClose);

      pageContentService.deleteByPage(id);
      pageRelationService.deleteByPage(id);
      pageTagService.deleteByPage(id);
      pageCommentService.deleteByPage(id);
      repository.delete(id);
    });
  }


  private Page decorate(Page page) {
    page.setContents(pageContentService.loadAll(page.getId()));
    page.setLinks(pageLinkService.loadSources(page.getId()));
    page.setTags(pageTagService.loadAll(page.getId()));
    page.setRelations(pageRelationService.loadAll(page.getId()));
    return page;
  }
}
