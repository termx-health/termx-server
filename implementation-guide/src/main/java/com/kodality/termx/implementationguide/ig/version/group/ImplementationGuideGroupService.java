package com.kodality.termx.implementationguide.ig.version.group;

import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideGroupService {
  private final ImplementationGuideGroupRepository repository;

  public void save(String ig, Long versionId, List<ImplementationGuideGroup> groups) {
    repository.retain(groups, ig, versionId);
    groups.forEach(g -> repository.save(g, ig, versionId));
  }
}
