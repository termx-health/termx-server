package com.kodality.termx.terminology.terminology.codesystem.entityproperty;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.designation.DesignationService;
import com.kodality.termx.terminology.terminology.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termx.ts.codesystem.DesignationQueryParams;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termx.ts.codesystem.EntityPropertyValueQueryParams;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class EntityPropertyService {
  private final EntityPropertyRepository repository;
  private final DesignationService designationService;
  private final EntityPropertyValueService entityPropertyValueService;

  public Optional<EntityProperty> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public List<EntityProperty> load(String codeSystem) {
    EntityPropertyQueryParams propertyParams = new EntityPropertyQueryParams();
    propertyParams.setCodeSystem(codeSystem);
    propertyParams.setSort(List.of("order-number"));
    propertyParams.all();
    return query(propertyParams).getData();
  }

  public QueryResult<EntityProperty> query(EntityPropertyQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public List<EntityProperty> save(String codeSystem, List<EntityProperty> entityProperties) {
    validate(codeSystem, entityProperties);

    repository.retain(codeSystem, entityProperties);
    if (entityProperties != null) {
      entityProperties.forEach(p -> {
        p.setCreated(p.getCreated() == null ? OffsetDateTime.now() : p.getCreated());
        repository.save(p, codeSystem);
      });
    }
    return entityProperties;
  }

  private void validate(String codeSystem, List<EntityProperty> entityProperties) {
    List<EntityProperty> existingProperties = load(codeSystem);
    existingProperties.forEach(ep -> {
      Optional<EntityProperty> property = entityProperties.stream().filter(p -> ep.getId().equals(p.getId())).findFirst();
      if (property.isPresent() && !property.get().getType().equals(ep.getType()) && checkPropertyUsed(ep.getId())) {
        throw ApiError.TE218.toApiException(Map.of("propertyName", ep.getName()));
      }
      if (property.isEmpty() && checkPropertyUsed(ep.getId())) {
        throw ApiError.TE203.toApiException(Map.of("propertyName", ep.getName()));
      }
    });
  }

  @Transactional
  public EntityProperty save(String codeSystem, EntityProperty entityProperty) {
    entityProperty.setCreated(entityProperty.getCreated() == null ? OffsetDateTime.now() : entityProperty.getCreated());
    repository.save(entityProperty, codeSystem);
    return entityProperty;
  }

  @Transactional
  public void cancel(String codeSystem, Long id) {
    if (checkPropertyUsed(id)) {
      throw ApiError.TE203.toApiException();
    }
    repository.cancel(codeSystem, id);
  }

  private boolean checkPropertyUsed(Long id) {
    EntityPropertyValueQueryParams propertyValueParams = new EntityPropertyValueQueryParams();
    propertyValueParams.setPropertyId(id);
    propertyValueParams.setLimit(0);

    DesignationQueryParams designationParams = new DesignationQueryParams();
    designationParams.setDesignationTypeId(id);
    designationParams.setLimit(0);
    return entityPropertyValueService.query(propertyValueParams).getMeta().getTotal() > 0 ||
           designationService.query(designationParams).getMeta().getTotal() > 0;
  }

  @Transactional
  public void deleteUsages(Long id) {
    if (load(id).isEmpty()) {
      throw new NotFoundException("property", id);
    }
    entityPropertyValueService.delete(id);
    designationService.delete(id);
  }
}
