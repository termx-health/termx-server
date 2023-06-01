package com.kodality.termserver.terminology.codesystem.entityproperty;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.terminology.codesystem.designation.DesignationService;
import com.kodality.termserver.terminology.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termserver.ts.codesystem.DesignationQueryParams;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.ts.codesystem.EntityPropertyValueQueryParams;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class EntityPropertyService {
  private final EntityPropertyRepository repository;
  private final DesignationService designationService;
  private final EntityPropertyValueService entityPropertyValueService;

  private final UserPermissionService userPermissionService;

  public Optional<EntityProperty> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public Optional<EntityProperty> load(String name, String codeSystem) {
    return Optional.ofNullable(repository.load(name, codeSystem));
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
  public List<EntityProperty> save(List<EntityProperty> entityProperties, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    validate(entityProperties, codeSystem);

    repository.retain(entityProperties, codeSystem);
    if (entityProperties != null) {
      entityProperties.forEach(p -> {
        p.setCreated(p.getCreated() == null ? OffsetDateTime.now() : p.getCreated());
        repository.save(p, codeSystem);
      });
    }
    return entityProperties;
  }

  private void validate(List<EntityProperty> entityProperties, String codeSystem) {
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
  public EntityProperty save(EntityProperty entityProperty, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    entityProperty.setCreated(entityProperty.getCreated() == null ? OffsetDateTime.now() : entityProperty.getCreated());
    repository.save(entityProperty, codeSystem);
    return entityProperty;
  }

  @Transactional
  public void cancel(Long id, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    if (checkPropertyUsed(id)) {
      throw ApiError.TE203.toApiException();
    }
    repository.cancel(id);
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
}
