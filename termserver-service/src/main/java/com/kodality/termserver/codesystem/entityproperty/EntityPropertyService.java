package com.kodality.termserver.codesystem.entityproperty;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import java.time.OffsetDateTime;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class EntityPropertyService {
  private final EntityPropertyRepository repository;

  public List<EntityProperty> getProperties(String codeSystem) {
    return repository.getProperties(codeSystem);
  }

  @Transactional
  public List<EntityProperty> save(List<EntityProperty> entityProperties, String codeSystem) {
    repository.retain(entityProperties, codeSystem);
    entityProperties.forEach(p -> {
      p.setCreated(p.getCreated() == null ? OffsetDateTime.now() : p.getCreated());
      repository.save(p, codeSystem);
    });
    return entityProperties;
  }

  public QueryResult<EntityProperty> query(EntityPropertyQueryParams params) {
    return repository.query(params);
  }


}
