package com.kodality.termx.sys.spacepackage.version;

import com.kodality.termx.sys.spacepackage.PackageVersion;
import com.kodality.termx.sys.spacepackage.resource.PackageResourceService;
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

  public PackageVersion load(Long id) {
    return repository.load(id);
  }

  public PackageVersion load(Long packageId, String version) {
    return repository.load(packageId, version);
  }
  public PackageVersion loadLastVersion(String spaceCode, String packageCode) {
    return repository.loadLastVersion(spaceCode, packageCode);
  }

  public List<PackageVersion> loadAll(Long packageId) {
    return repository.loadAll(packageId);
  }

  public void delete(Long id) {
    repository.delete(id);
  }
}
