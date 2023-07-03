package com.kodality.termx.terminology.mapset.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.mapset.entity.MapSetEntityService;
import com.kodality.termx.terminology.mapset.entity.MapSetEntityVersionService;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetEntity;
import com.kodality.termx.ts.mapset.MapSetEntityVersion;
import com.kodality.termx.ts.mapset.MapSetEntityVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetAssociationService {
  private final MapSetAssociationRepository repository;
  private final MapSetEntityService mapSetEntityService;
  private final MapSetEntityVersionService mapSetEntityVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final UserPermissionService userPermissionService;

  public QueryResult<MapSetAssociation> query(MapSetAssociationQueryParams params) {
    QueryResult<MapSetAssociation> associations = repository.query(params);
    decorate(associations.getData(), params.getMapSet(), params.getMapSetVersion());
    return associations;
  }

  public Optional<MapSetAssociation> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(a -> decorate(a, a.getMapSet(), null));
  }

  public Optional<MapSetAssociation> load(String mapSet, Long id) {
    return Optional.ofNullable(repository.load(mapSet, id)).map(a -> decorate(a, mapSet, null));
  }

  public Optional<MapSetAssociation> load(String mapSet, String mapSetVersion, Long id) {
    return query(new MapSetAssociationQueryParams()
        .setMapSet(mapSet)
        .setMapSetVersion(mapSetVersion)
        .setId(id)).findFirst().map(a -> decorate(a, mapSet, mapSetVersion));
  }

  private void decorate(List<MapSetAssociation> associations, String mapSet, String mapSetVersion) {
    if (CollectionUtils.isEmpty(associations)) {
      return;
    }
    MapSetEntityVersionQueryParams params = new MapSetEntityVersionQueryParams();
    params.setMapSetEntityIds(associations.stream().map(MapSetEntity::getId).map(String::valueOf).collect(Collectors.joining(",")));
    params.setMapSetVersion(mapSetVersion);
    params.setMapSet(mapSet);
    params.setLimit(-1);
    List<MapSetEntityVersion> versions = mapSetEntityVersionService.query(params).getData();
    associations.forEach(a -> a.setVersions(versions.stream().filter(v -> v.getMapSetEntityId().equals(a.getId())).toList()));

    List<CodeSystemEntityVersion> sources = getEntityVersions(associations.stream().map(MapSetAssociation::getSource).collect(Collectors.toList())).stream().peek(v -> v.setId(null)).toList();
    List<CodeSystemEntityVersion> targets = getEntityVersions(associations.stream().map(MapSetAssociation::getTarget).collect(Collectors.toList())).stream().peek(v -> v.setId(null)).toList();
    associations.forEach(a -> {
      a.setSource(sources.stream().filter(s -> s.getCode().equals(a.getSource().getCode()) && s.getCodeSystem().equals(a.getSource().getCodeSystem()) && (a.getSource().getId() == null || a.getSource().getId().equals(s.getId()))).findFirst().orElse(a.getSource()));
      a.setTarget(targets.stream().filter(t -> t.getCode().equals(a.getTarget().getCode()) && t.getCodeSystem().equals(a.getTarget().getCodeSystem()) && (a.getTarget().getId() == null || a.getTarget().getId().equals(t.getId()))).findFirst().orElse(a.getTarget()));
    });
  }


  private MapSetAssociation decorate(MapSetAssociation association, String mapSet, String mapSetVersion) {
    decorate(List.of(association), mapSet, mapSetVersion);
    return association;
  }

  private List<CodeSystemEntityVersion> getEntityVersions(List<CodeSystemEntityVersion> versions) {
    List<CodeSystemEntityVersion> definedVersions = versions.stream().filter(v -> v.getId() != null).toList();
    List<Pair<String, String>> undefinedVersions = versions.stream().filter(v -> v.getId() == null).map(v -> Pair.of(v.getCodeSystem(), v.getCode())).toList();

    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setIds(definedVersions.stream().map(CodeSystemEntityVersion::getId).map(String::valueOf).collect(Collectors.joining(",")));
    params.setLimit(definedVersions.size());
    List<CodeSystemEntityVersion> entityVersions = codeSystemEntityVersionService.query(params).getData();
    List<CodeSystemEntityVersion> v = codeSystemEntityVersionService.loadLastVersions(undefinedVersions);
    if (CollectionUtils.isEmpty(entityVersions)) {
      return v;
    }
    entityVersions.addAll(v);
    return entityVersions;
  }


  @Transactional
  public MapSetAssociation save(MapSetAssociation association, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    association.setMapSet(mapSet);
    mapSetEntityService.save(association);
    repository.save(association);
    mapSetEntityVersionService.save(association.getVersions(), association.getId());
    return association;
  }


  @Transactional
  public void cancel(Long id, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    mapSetEntityService.cancel(id);
    repository.cancel(id);
  }
}
