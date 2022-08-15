package com.kodality.termserver.ts.codesystem.entity;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.ts.codesystem.entitypropertyvalue.EntityPropertyValueService;
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
    return version;
  }

  public CodeSystemEntityVersion load(Long id) {
    return decorate(repository.load(id));
  }

  public QueryResult<CodeSystemEntityVersion> query(CodeSystemEntityVersionQueryParams params) {
    QueryResult<CodeSystemEntityVersion> versions = repository.query(params);
    versions.getData().forEach(this::decorate);
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

  @Transactional
  public void activate(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    userPermissionService.checkPermitted(currentVersion.getCodeSystem(), "CodeSystem", "edit");
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already activated, skipping activation process.", versionId);
      return;
    }
    repository.activate(versionId);
  }

  @Transactional
  public void retire(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    userPermissionService.checkPermitted(currentVersion.getCodeSystem(), "CodeSystem", "edit");
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already retired, skipping retirement process.", versionId);
      return;
    }
    repository.retire(versionId);
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
}
