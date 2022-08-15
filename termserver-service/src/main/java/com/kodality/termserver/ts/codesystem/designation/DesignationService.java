package com.kodality.termserver.ts.codesystem.designation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.DesignationQueryParams;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class DesignationService {
  private final DesignationRepository repository;

  private final UserPermissionService userPermissionService;

  public Optional<Designation> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public QueryResult<Designation> query(DesignationQueryParams params) {
    return repository.query(params);
  }

  public List<Designation> loadAll(Long codeSystemEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId);
  }

  @Transactional
  public void save(List<Designation> designations, Long codeSystemEntityVersionId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    repository.retain(designations, codeSystemEntityVersionId);
    if (designations != null) {
      designations.forEach(designation -> save(designation, codeSystemEntityVersionId, codeSystem));
    }
  }

  @Transactional
  public void save(Designation designation, Long codeSystemEntityVersionId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");
    repository.save(designation, codeSystemEntityVersionId);
  }

  @Transactional
  public void delete(Long id, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");
    repository.delete(id);
  }

}
