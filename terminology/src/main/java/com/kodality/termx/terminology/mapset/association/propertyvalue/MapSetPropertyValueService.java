package com.kodality.termx.terminology.mapset.association.propertyvalue;

import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.ts.mapset.MapSetPropertyValue;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetPropertyValueService {
  private final MapSetPropertyValueRepository repository;
  private final UserPermissionService userPermissionService;

  @Transactional
  public void save(List<MapSetPropertyValue> values, Long mapSetAssociationId, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    repository.retain(values, mapSetAssociationId);
    if (values != null) {
      values.forEach(value -> save(value, mapSetAssociationId, mapSet));
    }
  }

  @Transactional
  public void save(MapSetPropertyValue value, Long mapSetAssociationId, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    repository.save(value, mapSetAssociationId);
  }

  @Transactional
  public void batchUpsert(Map<Long, List<MapSetPropertyValue>> values, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    List<Entry<Long, List<MapSetPropertyValue>>> entries = values.entrySet().stream().toList();
    repository.retain(entries);
    repository.save(entries.stream().flatMap(e -> e.getValue().stream().map(v -> Pair.of(e.getKey(), v))).toList());
  }


  @Transactional
  public void delete(Long propertyId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");
    repository.delete(propertyId);
  }

}
