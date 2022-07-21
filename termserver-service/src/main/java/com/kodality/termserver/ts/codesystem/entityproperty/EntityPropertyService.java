package com.kodality.termserver.ts.codesystem.entityproperty;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class EntityPropertyService {
  private final EntityPropertyRepository repository;

  public Optional<EntityProperty> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public Optional<EntityProperty> load(String name, String codeSystem) {
    return Optional.ofNullable(repository.load(name, codeSystem));
  }

  public QueryResult<EntityProperty> query(EntityPropertyQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public List<EntityProperty> save(List<EntityProperty> entityProperties, String codeSystem) {
    repository.retain(entityProperties, codeSystem);
    if (entityProperties != null) {
      entityProperties.forEach(p -> {
        p.setCreated(p.getCreated() == null ? OffsetDateTime.now() : p.getCreated());
        repository.save(p, codeSystem);
      });
    }
    return entityProperties;
  }

  @Transactional
  public EntityProperty save(EntityProperty entityProperty, String codeSystem) {
    entityProperty.setCreated(entityProperty.getCreated() == null ? OffsetDateTime.now() : entityProperty.getCreated());
    repository.save(entityProperty, codeSystem);
    return entityProperty;
  }

  @Transactional
  public void delete(Long id) {
    repository.delete(id);
  }
}
