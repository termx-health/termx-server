package com.kodality.termserver.project.projectpackage.resource;

import com.kodality.termserver.project.projectpackage.PackageVersion.PackageResource;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PackageResourceService {
  private final PackageResourceRepository repository;

  @Transactional
  public void save(Long versionId, List<PackageResource> resources) {
    repository.retain(resources, versionId);
    if (CollectionUtils.isNotEmpty(resources)) {
      resources.forEach(r -> repository.save(versionId, r));
    }
  }

  public List<PackageResource> loadAll(String projectCode, String packageCode, String version) {
    return repository.loadAll(projectCode, packageCode, version);
  }
}
