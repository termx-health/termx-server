package com.kodality.termx.wiki.pagetag;

import com.kodality.termx.wiki.page.PageTag;
import com.kodality.termx.wiki.tag.TagService;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageTagService {
  private final TagService tagService;
  private final PageTagRepository repository;

  public List<PageTag> loadAll(Long pageId) {
    return repository.loadAll(pageId).stream().map(this::decorate).collect(Collectors.toList());
  }

  @Transactional
  public void save(List<PageTag> tags, Long pageId) {
    repository.retain(tags, pageId);
    if (CollectionUtils.isNotEmpty(tags)) {
      tags.forEach(t -> repository.save(prepare(t), pageId));
    }
  }

  @Transactional
  public void deleteByPage(Long pageId) {
    repository.deleteByPage(pageId);
  }

  private PageTag prepare(PageTag pageTag) {
    if (pageTag.getTag().getId() == null) {
      tagService.save(pageTag.getTag());
    }
    return pageTag;
  }

  private PageTag decorate(PageTag pageTag) {
    if (pageTag.getTag().getId() != null) {
      pageTag.setTag(tagService.load(pageTag.getTag().getId()));
    }
    return pageTag;
  }
}
