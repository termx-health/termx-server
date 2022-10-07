package com.kodality.termserver.thesaurus.pagecontent;

import com.github.slugify.Slugify;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import io.github.furstenheim.CopyDown;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageContentService {
  private final PageContentRepository repository;

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
  public void save(PageContent content, Long pageId) {
    repository.save(validate(prepare(content)), pageId);
  }

  private PageContent prepare(PageContent c) {
    String slug = Slugify.builder().build().slugify(c.getName());
    c.setSlug(slug);
    c.setContent(c.getContent() == null ? "" : c.getContent());
    if (c.getId() != null) {
      PageContent currentContent = load(c.getId());
      if (!currentContent.getContentType().equals(c.getContentType())) {
        c.setContent(convertContent(c.getContent(), currentContent.getContentType(), c.getContentType()));
      }
    }
    return c;
  }

  private String convertContent(String content, String fromType, String toType) {
    if ("html".equals(fromType) && "markdown".equals(toType)) {
      return new CopyDown().convert(content);
    }
    if ("markdown".equals(fromType) && "html".equals(toType)) {
      return HtmlRenderer.builder().build().render(Parser.builder().build().parse(content));
    }
    return content;
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
