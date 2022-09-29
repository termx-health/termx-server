package com.kodality.termserver.thesaurus.pagecontent;

import com.github.slugify.Slugify;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageContentService {
  private final PageContentRepository repository;

  public List<PageContent> loadAll(Long pageId) {
    return repository.loadAll(pageId);
  }

  public QueryResult<PageContent> query(PageContentQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(PageContent content, Long pageId) {
    repository.save(validate(prepare(content)), pageId);
  }

  private PageContent prepare(PageContent c) {
    String slug = Slugify.builder().build().slugify(c.getName());
    c.setSlug(slug);
    c.setContent(c.getContent() == null ? "" : c.getContent());
    return c;
  }

  private PageContent validate(PageContent c) {
    PageContentQueryParams params = new PageContentQueryParams();
    params.setSlug(c.getSlug());
    params.setLimit(1);
    Optional<PageContent> sameSlugContent = repository.query(params).findFirst();
    if (sameSlugContent.isPresent() && !sameSlugContent.get().getId().equals(c.getId())) {
      throw ApiError.THE101.toApiException(Map.of("slug", c.getSlug()));
    }
    return c;
  }

}
