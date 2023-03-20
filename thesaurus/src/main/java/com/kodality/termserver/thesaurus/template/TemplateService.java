package com.kodality.termserver.thesaurus.template;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.utils.TextUtil;
import io.micronaut.core.util.CollectionUtils;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class TemplateService {
  private final TemplateRepository repository;
  private final TemplateContentService contentService;

  public Optional<Template> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(this::decorate);
  }

  public QueryResult<Template> query(TemplateQueryParams params) {
    QueryResult<Template> res = repository.query(params);
    res.getData().forEach(this::decorate);
    return res;
  }

  @Transactional
  public Template save(Template template) {
    prepare(template);
    repository.save(template);
    contentService.save(template.getContents(), template.getId());
    return load(template.getId()).orElse(null);
  }

  @Transactional
  public void cancel(Long id) {
    repository.cancel(id);
  }

  private void prepare(Template template) {
    if (template.getId() != null && CollectionUtils.isNotEmpty(template.getContents())) {
      Template currentTemplate = load(template.getId()).orElseThrow();
      if (!currentTemplate.getContentType().equals(template.getContentType())) {
        template.getContents().forEach(c -> c.setContent(TextUtil.convertText(c.getContent(), currentTemplate.getContentType(), template.getContentType())));
      }
    }
  }

  private Template decorate(Template template) {
    template.setContents(contentService.loadAll(template.getId()));
    return template;
  }
}
