package com.kodality.termx.implementationguide.ig.version.group;

import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideGroupService {
  private final ImplementationGuideGroupRepository repository;

  @Transactional
  public void save(String ig, Long versionId, List<ImplementationGuideGroup> groups) {
    repository.retain(groups, ig, versionId);
    groups.forEach(g -> repository.save(g, ig, versionId));
  }
}
