package com.kodality.termx.terminology.terminology.codesystem.entitypropertyvalue;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.codesystem.EntityPropertyValueQueryParams;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class EntityPropertyValueService {
  private final EntityPropertyValueRepository repository;

  public List<EntityPropertyValue> loadAll(Long codeSystemEntityVersionId, Long baseEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId, baseEntityVersionId);
  }

  public Optional<EntityPropertyValue> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(List<EntityPropertyValue> values, Long codeSystemEntityVersionId) {
    repository.retain(values, codeSystemEntityVersionId);
    if (values != null) {
      values.stream().filter(v -> !v.isSupplement()).forEach(value -> save(value, codeSystemEntityVersionId));
    }
  }

  @Transactional
  public void batchUpsert(Map<Long, List<EntityPropertyValue>> values) {
    List<Entry<Long, List<EntityPropertyValue>>> entries = values.entrySet().stream().toList();
    repository.retain(entries);
    repository.save(entries.stream().flatMap(e -> e.getValue().stream().map(v -> Pair.of(e.getKey(), v))).toList());
  }

  @Transactional
  public void save(EntityPropertyValue value, Long codeSystemEntityVersionId) {
    repository.save(value, codeSystemEntityVersionId);
  }

  @Transactional
  public void delete(Long propertyId) {
    repository.delete(propertyId);
  }

  public QueryResult<EntityPropertyValue> query(EntityPropertyValueQueryParams params) {
    return repository.query(params);
  }

}
