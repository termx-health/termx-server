package com.kodality.termx.wiki.template;

import java.util.List;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class TemplateContentService {
  private final TemplateContentRepository repository;

  public TemplateContent load(Long contentId) {
    return repository.load(contentId);
  }

  public List<TemplateContent> loadAll(Long templateId) {
    return repository.loadAll(templateId);
  }

  @Transactional
  public void save(List<TemplateContent> contents, Long templateId) {
    repository.retain(contents, templateId);
    if (contents != null) {
      contents.forEach(content -> repository.save(prepare(content), templateId));
    }
  }

  private TemplateContent prepare(TemplateContent content) {
    if (content.getContent() == null) {
      content.setContent("");
    }
    return content;
  }

  public Optional<String> findContent(Long templateId, String lang) {
    if (templateId == null || lang == null) {
      return Optional.empty();
    }
    return repository.findContent(templateId, lang).stream().findFirst();
  }
}
