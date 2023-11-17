package com.kodality.termx.implementationguide.ig.version.page;

import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuidePageService {
  private final ImplementationGuidePageRepository repository;

  @Transactional
  public void save(String ig, Long versionId, List<ImplementationGuidePage> pages) {
    repository.retain(pages, ig, versionId);
    pages.forEach(p -> repository.save(p, ig, versionId));
  }

  public List<ImplementationGuidePage> loadAll(Long versionId) {
    return repository.loadAll(versionId);
  }
}
