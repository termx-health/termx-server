package com.kodality.termserver.ts.codesystem.entity;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
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

  @Transactional
  public CodeSystemEntityVersion save(CodeSystemEntityVersion version, Long codeSystemEntityId) {
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      throw ApiError.TE101.toApiException();
    }
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version, codeSystemEntityId);

    designationService.save(version.getDesignations(), version.getId());
    entityPropertyValueService.save(version.getPropertyValues(), version.getId());
    codeSystemAssociationService.save(prepareAssociations(version.getAssociations()), version.getId());
    return version;
  }

  public CodeSystemEntityVersion load(Long id) {
    return repository.load(id);
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
    CodeSystemEntityVersion currentVersion = repository.getVersion(versionId);
    if (currentVersion == null) {
      throw ApiError.TE108.toApiException(Map.of("version", versionId));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already activated, skipping activation process.", versionId);
      return;
    }
    repository.activate(versionId);
  }

  @Transactional
  public void retire(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.getVersion(versionId);
    if (currentVersion == null) {
      throw ApiError.TE108.toApiException(Map.of("version", versionId));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already retired, skipping retirement process.", versionId);
      return;
    }
    repository.retire(versionId);
  }
}
