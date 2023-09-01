package com.kodality.termx.terminology.mapset.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.mapset.version.MapSetVersionRepository;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Objects;
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
  private final MapSetVersionRepository mapSetVersionRepository;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final UserPermissionService userPermissionService;

  public QueryResult<MapSetAssociation> query(MapSetAssociationQueryParams params) {
    return repository.query(params);
  }

  public Optional<MapSetAssociation> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public Optional<MapSetAssociation> load(String mapSet, Long id) {
    return Optional.ofNullable(repository.load(mapSet, id));
  }

  public Optional<MapSetAssociation> load(String mapSet, String mapSetVersion, Long id) {
    return query(new MapSetAssociationQueryParams()
        .setMapSet(mapSet)
        .setMapSetVersion(mapSetVersion)
        .setId(id)).findFirst();
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
  public MapSetAssociation save(MapSetAssociation association, String mapSet, String version) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    association.setMapSet(mapSet);
    association.setMapSetVersion(mapSetVersionRepository.load(mapSet, version));
    repository.save(association);
    return association;
  }

  @Transactional
  public void batchSave(List<MapSetAssociation> associations, String mapSet, String version) {
    Long versionId = mapSetVersionRepository.load(mapSet, version).getId();

    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    repository.retain(mapSet, versionId, associations.stream().map(MapSetAssociation::getId).filter(Objects::nonNull).toList());
    repository.batchUpsert(associations, mapSet, versionId);
  }

  @Transactional
  public void batchUpsert(List<MapSetAssociation> associations, String mapSet, String version) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    repository.batchUpsert(associations, mapSet, mapSetVersionRepository.load(mapSet, version).getId());
  }

  @Transactional
  public void verify(List<Long> ids, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    repository.verify(ids);
  }

  @Transactional
  public void cancel(List<Long> ids, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    repository.cancel(ids);
  }
}
