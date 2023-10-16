package com.kodality.termx.core.sys.spacepackage.resource;

import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
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

  public List<PackageResource> loadAll(Long spaceId, String packageCode, String version) {
    return repository.loadAll(spaceId, packageCode, version);
  }

  public PackageResource load(Long id) {
    return repository.load(id);
  }
}
