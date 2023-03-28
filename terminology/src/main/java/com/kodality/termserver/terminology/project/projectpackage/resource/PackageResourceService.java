package com.kodality.termserver.terminology.project.projectpackage.resource;

import com.kodality.termserver.ts.project.projectpackage.PackageVersion.PackageResource;
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
      resources.forEach(r -> save(versionId, r));
    }
  }

  @Transactional
  public PackageResource save(Long versionId, PackageResource resource) {
    repository.save(versionId, resource);
    return resource;
  }

  public List<PackageResource> loadAll(String projectCode, String packageCode, String version) {
    return repository.loadAll(projectCode, packageCode, version);
  }

  public PackageResource load(Long id) {
    return repository.load(id);
  }
}