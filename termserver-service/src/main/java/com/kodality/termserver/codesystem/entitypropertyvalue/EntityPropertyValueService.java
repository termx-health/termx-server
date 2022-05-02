package com.kodality.termserver.codesystem.entitypropertyvalue;

import com.kodality.termserver.codesystem.EntityPropertyValue;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class EntityPropertyValueService {
  private final EntityPropertyValueRepository repository;

  public List<EntityPropertyValue> loadAll(Long codeSystemEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId);
  }

  @Transactional
  public void save(List<EntityPropertyValue> values, Long codeSystemEntityVersionId) {
    repository.retain(values, codeSystemEntityVersionId);
    repository.batchUpsert(values, codeSystemEntityVersionId);
  }

  @Transactional
  public void save(EntityPropertyValue value, Long codeSystemEntityVersionId) {
    repository.save(value, codeSystemEntityVersionId);
  }

}
