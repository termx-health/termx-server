package com.kodality.termserver.ts.codesystem.entitypropertyvalue;

import com.kodality.termserver.codesystem.EntityPropertyValue;
import java.util.List;
import java.util.Optional;
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

  public Optional<EntityPropertyValue> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(List<EntityPropertyValue> values, Long codeSystemEntityVersionId) {
    repository.retain(values, codeSystemEntityVersionId);
    if (values != null) {
      values.forEach(value -> save(value, codeSystemEntityVersionId));
    }
  }

  @Transactional
  public void save(EntityPropertyValue value, Long codeSystemEntityVersionId) {
    repository.save(value, codeSystemEntityVersionId);
  }

  @Transactional
  public void delete(Long id) {
    repository.delete(id);
  }
}
