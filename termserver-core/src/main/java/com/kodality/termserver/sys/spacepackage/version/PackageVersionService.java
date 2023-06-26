package com.kodality.termserver.sys.spacepackage.version;

import com.kodality.termserver.sys.spacepackage.PackageVersion;
import com.kodality.termserver.sys.spacepackage.resource.PackageResourceService;
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

  public List<PackageVersion> loadAll(Long packageId) {
    return repository.loadAll(packageId);
  }

  public void delete(Long id) {
    repository.delete(id);
  }
}
