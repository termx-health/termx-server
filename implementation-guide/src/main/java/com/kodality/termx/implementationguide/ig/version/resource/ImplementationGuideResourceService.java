package com.kodality.termx.implementationguide.ig.version.resource;

import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideResourceService {
  private final ImplementationGuideResourceRepository repository;

  @Transactional
  public void save(String ig, Long versionId, List<ImplementationGuideResource> resources) {
    repository.retain(resources, ig, versionId);
    resources.forEach(r -> repository.save(r, ig, versionId));
  }

  public List<ImplementationGuideResource> loadAll(Long versionId) {
    return repository.loadAll(versionId);
  }
}
