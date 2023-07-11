package com.kodality.termx.thesaurus.pagecontent;

import com.github.slugify.Slugify;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.thesaurus.ApiError;
import com.kodality.termx.thesaurus.page.Page;
import com.kodality.termx.thesaurus.page.PageContent;
import com.kodality.termx.thesaurus.page.PageContentQueryParams;
import com.kodality.termx.thesaurus.page.PageRepository;
import com.kodality.termx.thesaurus.pagerelation.PageRelationService;
import com.kodality.termx.thesaurus.template.TemplateContentService;
import com.kodality.termx.utils.TextUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageContentService {
  private final PageRepository pageRepository;
  private final PageContentRepository repository;
  private final PageRelationService pageRelationService;
  private final TemplateContentService templateContentService;

  public PageContent load(Long contentId) {
    return repository.load(contentId);
  }

  public List<PageContent> loadAll(Long pageId) {
    return repository.loadAll(pageId);
  }

  public QueryResult<PageContent> query(PageContentQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(PageContent c, Long pageId) {
    PageContent content = prepare(c, pageId);
    validate(content);

    repository.save(content, pageId);
    pageRelationService.save(content, pageId);
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

  private PageContent prepare(PageContent c, Long pageId) {
    Page page = pageRepository.load(pageId);

    c.setSlug(Slugify.builder().build().slugify(c.getName()));
    c.setContent(c.getContent() == null ? "" : c.getContent());
    c.setSpaceId(page.getSpaceId());

    if (c.getId() == null) {
      c.setContent(templateContentService.findContent(page.getTemplateId(), c.getLang()).orElse(c.getContent()));
    } else {
      PageContent currentContent = load(c.getId());
      if (!currentContent.getContentType().equals(c.getContentType())) {
        c.setContent(TextUtil.convertText(c.getContent(), currentContent.getContentType(), c.getContentType()));
      }
    }
    return c;
  }
}
