package com.kodality.termserver.thesaurus.pagelink;

import com.kodality.termserver.thesaurus.page.PageLink;
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
public class PageLinkService {
  private final PageLinkRepository repository;

  public List<PageLink> loadAll(Long pageId) {
    return repository.loadAll(pageId);
  }

  @Transactional
  public void save(List<PageLink> links, Long pageId) {
    repository.retain(links, pageId);
    if (CollectionUtils.isNotEmpty(links)) {
      links.forEach(l -> repository.save(l, pageId));
    }
    repository.refreshClosureView();
  }

  public List<Long> getPath(Long pageId) {
    Optional<String> path = repository.getPath(pageId).stream().findFirst();
    return path.isEmpty() ? List.of() : Arrays.stream(path.get().split("\\.")).filter(StringUtils::isNotEmpty).map(Long::valueOf).collect(Collectors.toList());
  }
}
