package com.kodality.termx.terminology.terminology.definedproperty;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.terminology.terminology.mapset.property.MapSetPropertyService;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termx.ts.mapset.MapSetProperty;
import com.kodality.termx.ts.mapset.MapSetPropertyQueryParams;
import com.kodality.termx.ts.property.DefinedProperty;
import com.kodality.termx.ts.property.DefinedPropertyQueryParams;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class DefinedPropertyService {
  private final DefinedPropertyRepository repository;
  private final EntityPropertyService entityPropertyService;
  private final MapSetPropertyService mapSetPropertyService;

  public Optional<DefinedProperty> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public QueryResult<DefinedProperty> query(DefinedPropertyQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(DefinedProperty entityProperty) {
    repository.save(entityProperty);
  }

  @Transactional
  public void updateRelated(Long id) {
    DefinedProperty definedProperty = load(id).orElse(null);
    if (definedProperty == null) {
      return;
    }

    //CS
    List<EntityProperty> relatedCSProperties = entityPropertyService.query(new EntityPropertyQueryParams().setDefinedEntityPropertyId(id).all()).getData();
    relatedCSProperties.forEach(p -> p.setDescription(definedProperty.getDescription()));
    relatedCSProperties.forEach(p -> entityPropertyService.save(p.getCodeSystem(), p));

    //MS
    List<MapSetProperty> relatedMSProperties = mapSetPropertyService.query(new MapSetPropertyQueryParams().setDefinedEntityPropertyId(id).all()).getData();
    relatedMSProperties.forEach(p -> p.setDescription(definedProperty.getDescription()));
    relatedMSProperties.forEach(p -> mapSetPropertyService.save(p, p.getMapSet()));
  }
}
