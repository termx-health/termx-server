package com.kodality.termx.terminology.mapset.property;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ApiError;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.ts.mapset.MapSetProperty;
import com.kodality.termx.ts.mapset.MapSetPropertyQueryParams;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetPropertyService {
  private final MapSetPropertyRepository repository;
  private final UserPermissionService userPermissionService;

  public Optional<MapSetProperty> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public Optional<MapSetProperty> load(String name, String mapSet) {
    return Optional.ofNullable(repository.load(name, mapSet));
  }

  public List<MapSetProperty> load(String mapSet) {
    MapSetPropertyQueryParams propertyParams = new MapSetPropertyQueryParams();
    propertyParams.setMapSet(mapSet);
    propertyParams.setSort(List.of("order-number"));
    propertyParams.all();
    return query(propertyParams).getData();
  }

  public QueryResult<MapSetProperty> query(MapSetPropertyQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public List<MapSetProperty> save(List<MapSetProperty> properties, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    validate(properties, codeSystem);

    repository.retain(properties, codeSystem);
    if (properties != null) {
      properties.forEach(p -> {
        p.setCreated(p.getCreated() == null ? OffsetDateTime.now() : p.getCreated());
        repository.save(p, codeSystem);
      });
    }
    return properties;
  }

  private void validate(List<MapSetProperty> properties, String mapSet) {
   List<MapSetProperty> existingProperties = load(mapSet);
   existingProperties.forEach(ep -> {
     Optional<MapSetProperty> property = properties.stream().filter(p -> ep.getId().equals(p.getId())).findFirst();
     if (property.isPresent() && !property.get().getType().equals(ep.getType()) && checkPropertyUsed(ep.getId())) {
       throw ApiError.TE218.toApiException(Map.of("propertyName", ep.getName()));
     }
     if (property.isEmpty() && checkPropertyUsed(ep.getId())) {
       throw ApiError.TE203.toApiException(Map.of("propertyName", ep.getName()));
     }
   });
  }

  @Transactional
  public MapSetProperty save(MapSetProperty property, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    property.setCreated(property.getCreated() == null ? OffsetDateTime.now() : property.getCreated());
    repository.save(property, mapSet);
    return property;
  }

  @Transactional
  public void cancel(Long id, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    if (checkPropertyUsed(id)) {
      throw ApiError.TE203.toApiException();
    }
    repository.cancel(id);
  }

  private boolean checkPropertyUsed(Long id) {
    //TODO
    return false;
  }

  @Transactional
  public void deleteUsages(Long id, String codeSystem) {
    //TODO
  }
}
