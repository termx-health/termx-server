package com.kodality.termserver.ts.codesystem.entity;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.DesignationQueryParams;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.EntityPropertyValueQueryParams;
import com.kodality.termserver.ts.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.ts.codesystem.concept.ConceptRefreshViewJob;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.ts.codesystem.entitypropertyvalue.EntityPropertyValueService;
import io.micronaut.core.util.CollectionUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemEntityVersionService {
  private final DesignationService designationService;
  private final EntityPropertyValueService entityPropertyValueService;
  private final CodeSystemAssociationService codeSystemAssociationService;
  private final CodeSystemEntityVersionRepository repository;
  private final ConceptRefreshViewJob conceptRefreshViewJob;

  private final UserPermissionService userPermissionService;

  @Transactional
  public CodeSystemEntityVersion save(CodeSystemEntityVersion version, Long codeSystemEntityId) {
    userPermissionService.checkPermitted(version.getCodeSystem(), "CodeSystem", "edit");

    if (!PublicationStatus.draft.equals(version.getStatus())) {
      throw ApiError.TE101.toApiException();
    }
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version, codeSystemEntityId);

    designationService.save(version.getDesignations(), version.getId(), version.getCodeSystem());
    entityPropertyValueService.save(version.getPropertyValues(), version.getId(), version.getCodeSystem());
    codeSystemAssociationService.save(prepareAssociations(version.getAssociations()), version.getId(), version.getCodeSystem());
    conceptRefreshViewJob.refreshView();
    return version;
  }

  @Transactional
  public void batchSave(Map<Long, CodeSystemEntityVersion> versions, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");
    versions.values().forEach(v -> {
      if (!PublicationStatus.draft.equals(v.getStatus())) {
        throw ApiError.TE101.toApiException();
      }
      v.setCreated(v.getCreated() == null ? OffsetDateTime.now() : v.getCreated());
    });

    repository.batchUpsert(versions);

    Map<Long, List<Designation>> designations = versions.values().stream().collect(Collectors.toMap(CodeSystemEntityVersion::getId, CodeSystemEntityVersion::getDesignations));
    Map<Long, List<EntityPropertyValue>> propertyValues = versions.values().stream().collect(Collectors.toMap(CodeSystemEntityVersion::getId, CodeSystemEntityVersion::getPropertyValues));
    Map<Long, List<CodeSystemAssociation>> associations = versions.values().stream().collect(Collectors.toMap(CodeSystemEntityVersion::getId, ev -> prepareAssociations(ev.getAssociations())));
    designationService.batchUpsert(designations, codeSystem);
    entityPropertyValueService.batchUpsert(propertyValues, codeSystem);
    codeSystemAssociationService.batchUpsert(associations, codeSystem);
    conceptRefreshViewJob.refreshView();
  }

  public CodeSystemEntityVersion load(Long id) {
    return decorate(repository.load(id));
  }

  public QueryResult<CodeSystemEntityVersion> query(CodeSystemEntityVersionQueryParams params) {
    QueryResult<CodeSystemEntityVersion> versions = repository.query(params);
    versions.setData(decorate(versions.getData()));
    return versions;
  }

  private List<CodeSystemAssociation> prepareAssociations(List<CodeSystemAssociation> associations) {
    if (associations == null) {
      return new ArrayList<>();
    }
    return associations.stream().filter(a -> a.getTargetId() != null).collect(Collectors.toList());
  }

  public CodeSystemEntityVersion decorate(CodeSystemEntityVersion version) {
    version.setDesignations(designationService.loadAll(version.getId()));
    version.setPropertyValues(entityPropertyValueService.loadAll(version.getId()));
    version.setAssociations(codeSystemAssociationService.loadAll(version.getId()));
    return version;
  }

  public List<CodeSystemEntityVersion> decorate(List<CodeSystemEntityVersion> versions) {
    if (CollectionUtils.isEmpty(versions)) {
      return versions;
    }
    String versionIds = versions.stream().map(CodeSystemEntityVersion::getId).map(String::valueOf).collect(Collectors.joining(","));

    DesignationQueryParams designationParams = new DesignationQueryParams().setCodeSystemEntityVersionId(versionIds);
    designationParams.setLimit(-1);
    List<Designation> designations = designationService.query(designationParams).getData();
    EntityPropertyValueQueryParams entityPropertyValueParams = new EntityPropertyValueQueryParams().setCodeSystemEntityVersionId(versionIds);
    entityPropertyValueParams.setLimit(-1);
    List<EntityPropertyValue> propertyValues = entityPropertyValueService.query(entityPropertyValueParams).getData();
    CodeSystemAssociationQueryParams codeSystemAssociationParams = new CodeSystemAssociationQueryParams().setCodeSystemEntityVersionId(versionIds);
    codeSystemAssociationParams.setLimit(-1);
    List<CodeSystemAssociation> associations = codeSystemAssociationService.query(codeSystemAssociationParams).getData();

    versions.forEach(v -> {
      v.setDesignations(designations.stream().filter(d -> d.getCodeSystemEntityVersionId().equals(v.getId())).collect(Collectors.toList()));
      v.setPropertyValues(propertyValues.stream().filter(pv -> pv.getCodeSystemEntityVersionId().equals(v.getId())).collect(Collectors.toList()));
      v.setAssociations(associations.stream().filter(a -> a.getSourceId().equals(v.getId())).collect(Collectors.toList()));
    });
    return versions;
  }

  @Transactional
  public void activate(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    userPermissionService.checkPermitted(currentVersion.getCodeSystem(), "CodeSystem", "publish");
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already activated, skipping activation process.", versionId);
      return;
    }
    repository.activate(versionId);
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public void activate(List<Long> versionIds) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setIds(versionIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
    params.setLimit(versionIds.size());
    List<CodeSystemEntityVersion> currentVersions = repository.query(params).getData();
    if (currentVersions.size() != versionIds.size()) {
      throw ApiError.TE109.toApiException();
    }
    currentVersions.forEach(currentVersion -> userPermissionService.checkPermitted(currentVersion.getCodeSystem(), "CodeSystem", "publish"));
    repository.activate(versionIds);
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public void retire(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    userPermissionService.checkPermitted(currentVersion.getCodeSystem(), "CodeSystem", "publish");
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already retired, skipping retirement process.", versionId);
      return;
    }
    repository.retire(versionId);
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public void saveAsDraft(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    userPermissionService.checkPermitted(currentVersion.getCodeSystem(), "CodeSystem", "publish");
    if (PublicationStatus.draft.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already draft, skipping retirement process.", versionId);
      return;
    }
    repository.saveAsDraft(versionId);
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public void duplicate(String codeSystem, Long entityId, Long id) {
    CodeSystemEntityVersion version = load(id);
    version.setId(null);
    version.setCreated(null);
    version.setStatus(PublicationStatus.draft);
    version.setCodeSystem(codeSystem);
    if (version.getDesignations() != null) {
      version.getDesignations().forEach(d -> d.setId(null));
    }
    if (version.getPropertyValues() != null) {
      version.getPropertyValues().forEach(pv -> pv.setId(null));
    }
    if (version.getAssociations() != null) {
      version.getAssociations().forEach(a -> a.setId(null));
    }
    save(version, entityId);
  }

  @Transactional
  public void cancel(Long id, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    designationService.save(List.of(), id, codeSystem);
    entityPropertyValueService.save(List.of(), id, codeSystem);
    codeSystemAssociationService.save(List.of(), id, codeSystem);

    repository.cancel(id);
    conceptRefreshViewJob.refreshView();
  }
}
