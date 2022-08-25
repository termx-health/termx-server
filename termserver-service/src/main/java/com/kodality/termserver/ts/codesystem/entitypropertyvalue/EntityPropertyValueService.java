package com.kodality.termserver.ts.codesystem.entitypropertyvalue;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.codesystem.EntityPropertyValueQueryParams;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class EntityPropertyValueService {
  private final EntityPropertyValueRepository repository;

  private final UserPermissionService userPermissionService;

  public List<EntityPropertyValue> loadAll(Long codeSystemEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId);
  }

  public Optional<EntityPropertyValue> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(List<EntityPropertyValue> values, Long codeSystemEntityVersionId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    repository.retain(values, codeSystemEntityVersionId);
    if (values != null) {
      values.forEach(value -> save(value, codeSystemEntityVersionId, codeSystem));
    }
  }

  @Transactional
  public void save(EntityPropertyValue value, Long codeSystemEntityVersionId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");
    repository.save(value, codeSystemEntityVersionId);
  }

  @Transactional
  public void delete(Long id, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");
    repository.delete(id);
  }

  public QueryResult<EntityPropertyValue> query(EntityPropertyValueQueryParams params) {
    return repository.query(params);
  }
}
