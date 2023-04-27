package com.kodality.termserver.terminology.codesystem.entity;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.terminology.codesystem.concept.ConceptRefreshViewJob;
import com.kodality.termserver.terminology.codesystem.designation.DesignationService;
import com.kodality.termserver.terminology.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termserver.ts.CodeSystemExternalProvider;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.DesignationQueryParams;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.EntityPropertyValueQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
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
  private final List<CodeSystemExternalProvider> codeSystemExternalProviders;

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

    long start = System.currentTimeMillis();
    repository.batchUpsert(versions);
    log.info("Versions saved ({} sec)", (System.currentTimeMillis() - start) / 1000);
    start = System.currentTimeMillis();

    Map<Long, List<Designation>> designations = versions.values().stream().collect(Collectors.toMap(CodeSystemEntityVersion::getId, CodeSystemEntityVersion::getDesignations));
    Map<Long, List<EntityPropertyValue>> propertyValues = versions.values().stream().collect(Collectors.toMap(CodeSystemEntityVersion::getId, CodeSystemEntityVersion::getPropertyValues));
    Map<Long, List<CodeSystemAssociation>> associations = versions.values().stream().collect(Collectors.toMap(CodeSystemEntityVersion::getId, ev -> prepareAssociations(ev.getAssociations())));
    designationService.batchUpsert(designations, codeSystem);
    log.info("Designations saved '{}' ({} sec)", designations.values().stream().flatMap(Collection::stream).toList().size(), (System.currentTimeMillis() - start) / 1000);
    start = System.currentTimeMillis();
    entityPropertyValueService.batchUpsert(propertyValues, codeSystem);
    log.info("Properties saved '{}' ({} sec)", propertyValues.values().stream().flatMap(Collection::stream).toList().size(), (System.currentTimeMillis() - start) / 1000);
    start = System.currentTimeMillis();
    codeSystemAssociationService.batchUpsert(associations, codeSystem);
    log.info("Associations saved '{}' ({} sec)", associations.values().stream().flatMap(Collection::stream).toList().size(), (System.currentTimeMillis() - start) / 1000);
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
  public void activate(List<Long> versionIds, String codeSystem) {
    long start = System.currentTimeMillis();
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "publish");
    repository.activate(versionIds);
    conceptRefreshViewJob.refreshView();
    log.info("Activated (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
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

  public List<CodeSystemEntityVersion> loadLastVersions(List<Pair<String, String>> versions) {
    List<CodeSystemEntityVersion> codeSystemEntityVersions = new ArrayList<>();

    versions.stream().collect(Collectors.groupingBy(Pair::getKey)).forEach((key, value) -> {
      List<String> codes = value.stream().map(Pair::getValue).collect(Collectors.toSet()).stream().toList();
      for (CodeSystemExternalProvider provider : codeSystemExternalProviders) {
        List<CodeSystemEntityVersion> csVersions = provider.loadLastVersions(key, codes);
        codeSystemEntityVersions.addAll(csVersions);
      }
      codeSystemEntityVersions.addAll(repository.loadLastVersions(key, codes));
    });

    return codeSystemEntityVersions.stream().filter(Objects::nonNull).collect(Collectors.toList());
  }
}
