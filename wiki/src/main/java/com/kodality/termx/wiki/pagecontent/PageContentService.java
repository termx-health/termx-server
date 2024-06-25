package com.kodality.termx.wiki.pagecontent;

import com.github.slugify.Slugify;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.utils.TextUtil;
import com.kodality.termx.wiki.ApiError;
import com.kodality.termx.wiki.page.Page;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentQueryParams;
import com.kodality.termx.wiki.page.PageCommentStatus;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageContentHistoryItem;
import com.kodality.termx.wiki.page.PageContentHistoryQueryParams;
import com.kodality.termx.wiki.page.PageContentQueryParams;
import com.kodality.termx.wiki.page.PageRepository;
import com.kodality.termx.wiki.pagecomment.PageCommentService;
import com.kodality.termx.wiki.pagerelation.PageRelationService;
import com.kodality.termx.wiki.template.TemplateContentService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageContentService {
  private final PageRepository pageRepository;
  private final PageContentRepository repository;
  private final PageRelationService pageRelationService;
  private final PageCommentService pageCommentService;
  private final TemplateContentService templateContentService;
  private final ProvenanceService provenanceService;
  private final PageContentHistoryRepository historyRepository;

  public List<PageContent> loadAll(Long pageId) {
    return repository.loadAll(pageId);
  }

  public PageContent load(Long contentId) {
    return repository.load(contentId);
  }

  public QueryResult<PageContent> query(PageContentQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public PageContent save(PageContent c, Long pageId) {
    PageContent content = prepare(c, pageId);
    validate(content);

    PageContent persisted = load(content.getId());
    if (persisted != null) {
      recalculateComments(persisted, content);
    }

    Long contentId = repository.save(content, pageId);
    provenanceService.create(persisted != null
        ? new Provenance("modified", "PageContent", contentId.toString())
        : new Provenance("created", "PageContent", contentId.toString()));
    content.setId(contentId);
    pageRelationService.save(content, pageId);

    PageContent saved = load(contentId);
    historyRepository.persist(saved);

    return saved;
  }

  @Transactional
  public void delete(Long contentId) {
    //TODO: validate child links empty?
    Long pageId = load(contentId).getPageId();

    provenanceService.create(new Provenance("deleted", "PageContent", contentId.toString()));
    repository.delete(contentId);
  }

  @Transactional
  public void deleteByPage(Long pageId) {
    for (PageContent content : loadAll(pageId)) {
      delete(content.getId());
    }
  }


  public QueryResult<PageContentHistoryItem> queryHistory(PageContentHistoryQueryParams params) {
    return historyRepository.query(params);
  }


  private PageContent prepare(PageContent c, Long pageId) {
    Page page = pageRepository.load(pageId);

    c.setSlug(Slugify.builder().build().slugify(c.getName()));
    c.setContent(c.getContent() == null ? "" : c.getContent());
    c.setSpaceId(page.getSpaceId());

    if (c.getId() == null) {
      Long templateId = page.getSettings() != null ? page.getSettings().getTemplateId() : null;
      c.setContent(templateContentService.findContent(templateId, c.getLang()).orElse(c.getContent()));
    } else {
      PageContent currentContent = load(c.getId());
      if (!currentContent.getContentType().equals(c.getContentType())) {
        c.setContent(TextUtil.convertText(c.getContent(), currentContent.getContentType(), c.getContentType()));
      }
    }
    return c;
  }

  private void validate(PageContent c) {
    PageContentQueryParams params = new PageContentQueryParams();
    params.setSlugs(c.getSlug());
    params.setSpaceIds(c.getSpaceId().toString());
    params.setLimit(1);
    Optional<PageContent> sameSlugContent = repository.query(params).findFirst();
    if (sameSlugContent.isPresent() && !sameSlugContent.get().getId().equals(c.getId())) {
      throw ApiError.T000.toApiException(Map.of("slug", c.getSlug()));
    }
  }

  private void recalculateComments(PageContent persisted, PageContent current) {
    PageCommentQueryParams q = new PageCommentQueryParams();
    q.setPageContentIds(current.getId().toString());
    q.setStatuses(PageCommentStatus.active);
    q.all();
    List<PageComment> comments = pageCommentService.query(q).getData();
    pageCommentService.recalculateLineNumbers(comments, persisted.getContent(), current.getContent());
  }
}
