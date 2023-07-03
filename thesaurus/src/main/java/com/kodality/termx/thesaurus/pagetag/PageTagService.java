package com.kodality.termx.thesaurus.pagetag;

import com.kodality.termx.thesaurus.tag.TagService;
import com.kodality.termx.thesaurus.page.PageTag;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
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
