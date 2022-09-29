package com.kodality.termserver.thesaurus.pagerelation;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PageRelationService {
  private final PageRelationRepository repository;

  public List<PageRelation> loadAll(Long pageId) {
    return repository.loadAll(pageId);
  }

  @Transactional
  public void save(List<PageRelation> relations, Long pageId) {
    repository.retain(relations, pageId);
    if (CollectionUtils.isNotEmpty(relations)) {
      relations.forEach(r -> repository.save(r, pageId));
    }
    repository.refreshClosureView();
  }

  public List<Long> getPath(Long pageId) {
    Optional<String> path = repository.getPath(pageId).stream().findFirst();
    return path.isEmpty() ? List.of() : Arrays.stream(path.get().split("\\.")).filter(StringUtils::isNotEmpty).map(Long::valueOf).collect(Collectors.toList());
  }
}
