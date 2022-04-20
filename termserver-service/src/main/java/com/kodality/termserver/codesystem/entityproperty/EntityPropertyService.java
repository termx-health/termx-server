package com.kodality.termserver.codesystem.entityproperty;

import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.commons.model.model.QueryResult;
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
  public EntityProperty save(EntityProperty entityProperty, String codeSystem) {

    entityProperty.setCreated(entityProperty.getCreated() == null ? OffsetDateTime.now() : entityProperty.getCreated());
    repository.save(entityProperty, codeSystem);
    return entityProperty;
  }

  public QueryResult<EntityProperty> query(EntityPropertyQueryParams params) {
    return repository.query(params);
  }


}
