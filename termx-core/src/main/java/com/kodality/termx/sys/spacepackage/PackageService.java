package com.kodality.termx.sys.spacepackage;

import com.kodality.termx.sys.spacepackage.version.PackageVersionService;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PackageService {
  private final PackageRepository repository;
  private final PackageVersionService versionService;

  @Transactional
  public Package save(Package p, Long spaceId) {
    repository.save(p, spaceId);
    return p;
  }

  @Transactional
  public Package save(PackageTransactionRequest request, Long spaceId) {
    Package p = request.getPack();
    repository.save(p, spaceId);
    versionService.save(p.getId(), request.getVersion());
    return p;
  }

  public Package load(Long spaceId, Long id) {
    return repository.load(spaceId, id);
  }

  public Package load(Long spaceId, String code) {
    return repository.load(spaceId, code);
  }

  public List<Package> loadAll(Long spaceId) {
    return repository.loadAll(spaceId);
  }

  public void delete(Long spaceId, Long id) {
    repository.delete(spaceId, id);
  }
}
