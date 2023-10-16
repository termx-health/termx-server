package com.kodality.termx.core.sys.spacepackage.version;

import com.kodality.termx.core.sys.spacepackage.resource.PackageResourceService;
import com.kodality.termx.sys.spacepackage.PackageVersion;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PackageVersionService {
  private final PackageVersionRepository repository;
  private final PackageResourceService resourceService;

  @Transactional
  public void save(Long packageId, PackageVersion version) {
    repository.save(packageId, version);
    resourceService.save(version.getId(), version.getResources());
  }

  public List<PackageVersion> loadAll(Long spaceId, Long packageId) {
    return repository.loadAll(spaceId, packageId);
  }

  public PackageVersion load(Long spaceId, Long packageId, Long versionId) {
    return repository.load(spaceId, packageId, versionId);
  }
  public PackageVersion loadLastVersion(String spaceCode, String packageCode) {
    return repository.loadLastVersion(spaceCode, packageCode);
  }

  public PackageVersion load(Long packageId, String version) {
    return repository.load(packageId, version);
  }

  public void delete(Long spaceId, Long packageId, Long versionId) {
    repository.delete(spaceId, packageId, versionId);
  }
}
