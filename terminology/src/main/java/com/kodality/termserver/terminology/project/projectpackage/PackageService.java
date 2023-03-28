package com.kodality.termserver.terminology.project.projectpackage;

import com.kodality.termserver.ts.project.projectpackage.Package;
import com.kodality.termserver.ts.project.projectpackage.PackageTransactionRequest;
import com.kodality.termserver.terminology.project.projectpackage.version.PackageVersionService;
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
  public Package save(Package p, Long projectId) {
    repository.save(p, projectId);
    return p;
  }

  @Transactional
  public Package save(PackageTransactionRequest request, Long projectId) {
    Package p = request.getPack();
    repository.save(p, projectId);
    versionService.save(p.getId(), request.getVersion());
    return p;
  }

  public Package load(Long id) {
    return repository.load(id);
  }

  public Package load(Long projectId, String code) {
    return repository.load(projectId, code);
  }

  public List<Package> loadAll(Long projectId) {
    return repository.loadAll(projectId);
  }

  public void delete(Long id) {
    repository.delete(id);
  }
}