package com.kodality.termx.terminology.terminology.codesystem.entity;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ts.CodeSystemExternalProvider;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptRefreshViewJob;
import com.kodality.termx.terminology.terminology.codesystem.designation.DesignationService;
import com.kodality.termx.terminology.terminology.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.DesignationQueryParams;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.codesystem.EntityPropertyValueQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
  private final List<CodeSystemExternalProvider> codeSystemProviders;

  public void validate(String codeSystem, Long versionId) {
    if (!repository.exists(codeSystem, versionId)) {
      throw new NotFoundException("CodeSystemEntityVersion", versionId);
    }
  }

  @Transactional
  public CodeSystemEntityVersion save(CodeSystemEntityVersion version, Long codeSystemEntityId) {
    validate(version);
    prepare(version);
    repository.save(version, codeSystemEntityId);

    designationService.save(version.getDesignations(), version.getId());
    entityPropertyValueService.save(version.getPropertyValues(), version.getId());
    codeSystemAssociationService.save(prepareAssociations(version.getAssociations()), version.getId(), version.getCodeSystem());
    conceptRefreshViewJob.refreshView();
    return version;
  }

  @Transactional
  public void batchSave(Map<Long, List<CodeSystemEntityVersion>> versions, String codeSystem) {
    versions.values().forEach(versionList -> versionList.forEach(v -> {
      validate(v);
      prepare(v);
    }));

    long start = System.currentTimeMillis();
    repository.batchUpsert(versions);
    log.info("Versions saved ({} sec)", (System.currentTimeMillis() - start) / 1000);
    start = System.currentTimeMillis();

    Map<Long, List<Designation>> designations = versions.values().stream().flatMap(Collection::stream).filter(v -> v.getDesignations() != null)
        .collect(Collectors.toMap(CodeSystemEntityVersion::getId, CodeSystemEntityVersion::getDesignations));
    Map<Long, List<EntityPropertyValue>> propertyValues = versions.values().stream().flatMap(Collection::stream).filter(v -> v.getPropertyValues() != null)
        .collect(Collectors.toMap(CodeSystemEntityVersion::getId, CodeSystemEntityVersion::getPropertyValues));
    Map<Long, List<CodeSystemAssociation>> associations = versions.values().stream().flatMap(Collection::stream).filter(v -> v.getAssociations() != null)
        .collect(Collectors.toMap(CodeSystemEntityVersion::getId, ev -> prepareAssociations(ev.getAssociations())));
    designationService.batchUpsert(designations);
    log.info("Designations saved '{}' ({} sec)", designations.values().stream().flatMap(Collection::stream).toList().size(),
        (System.currentTimeMillis() - start) / 1000);
    start = System.currentTimeMillis();
    entityPropertyValueService.batchUpsert(propertyValues);
    log.info("Properties saved '{}' ({} sec)", propertyValues.values().stream().flatMap(Collection::stream).toList().size(),
        (System.currentTimeMillis() - start) / 1000);
    start = System.currentTimeMillis();
    codeSystemAssociationService.batchUpsert(associations, codeSystem);
    log.info("Associations saved '{}' ({} sec)", associations.values().stream().flatMap(Collection::stream).toList().size(),
        (System.currentTimeMillis() - start) / 1000);
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
    if (version == null) {
      return null;
    }
    System.out.println("here");
    version.setDesignations(designationService.loadAll(version.getId(), version.getBaseEntityVersionId()));
    version.setPropertyValues(entityPropertyValueService.loadAll(version.getId(), version.getBaseEntityVersionId()));
    version.setAssociations(codeSystemAssociationService.loadAll(version.getId(), version.getBaseEntityVersionId()));
    return version;
  }

  public List<CodeSystemEntityVersion> decorate(List<CodeSystemEntityVersion> versions) {
    if (CollectionUtils.isEmpty(versions)) {
      return versions;
    }
    IntStream.range(0, (versions.size() + 10000 - 1) / 10000)
        .mapToObj(i -> versions.subList(i * 10000, Math.min(versions.size(), (i + 1) * 10000))).forEach(batch -> {
          String versionIds = String.join(",", batch.stream()
              .flatMap(v -> Stream.of(v.getId(), v.getBaseEntityVersionId())).filter(Objects::nonNull).map(String::valueOf)
              .collect(Collectors.groupingBy(id -> id)).keySet());

          DesignationQueryParams designationParams = new DesignationQueryParams().setCodeSystemEntityVersionId(versionIds);
          designationParams.setLimit(-1);
          Map<Long, List<Designation>> designations = designationService.query(designationParams).getData().stream().collect(Collectors.groupingBy(Designation::getCodeSystemEntityVersionId));
          EntityPropertyValueQueryParams entityPropertyValueParams = new EntityPropertyValueQueryParams().setCodeSystemEntityVersionId(versionIds);
          entityPropertyValueParams.setLimit(-1);
          Map<Long, List<EntityPropertyValue>> propertyValues = entityPropertyValueService.query(entityPropertyValueParams).getData().stream().collect(Collectors.groupingBy(EntityPropertyValue::getCodeSystemEntityVersionId));
          CodeSystemAssociationQueryParams codeSystemAssociationParams = new CodeSystemAssociationQueryParams().setSourceEntityVersionId(versionIds);
          codeSystemAssociationParams.setLimit(-1);
          Map<Long, List<CodeSystemAssociation>> associations = codeSystemAssociationService.query(codeSystemAssociationParams).getData().stream().collect(Collectors.groupingBy(CodeSystemAssociation::getSourceId));

          batch.forEach(v -> {
            v.setDesignations(designations.getOrDefault(v.getId(), new ArrayList<>()));
            v.setPropertyValues(propertyValues.getOrDefault(v.getId(), new ArrayList<>()));
            v.setAssociations(associations.getOrDefault(v.getId(), new ArrayList<>()));

            if (v.getBaseEntityVersionId() != null) {
              v.getDesignations().addAll(designations.getOrDefault(v.getBaseEntityVersionId(), new ArrayList<>()).stream().map(d -> d.setSupplement(true)).toList());
              v.getPropertyValues().addAll(propertyValues.getOrDefault(v.getBaseEntityVersionId(), new ArrayList<>()).stream().map(p -> p.setSupplement(true)).toList());
              v.getAssociations().addAll(associations.getOrDefault(v.getBaseEntityVersionId(), new ArrayList<>()).stream().map(a -> a.setSupplement(true)).toList());
            }
          });
        });

    versions.stream().filter(v -> v.getCodeSystemBase() != null).collect(Collectors.groupingBy(CodeSystemEntityVersion::getCodeSystemBase)).entrySet().forEach(es -> {
      codeSystemProviders.forEach(provider -> {
        Map<String, List<CodeSystemEntityVersion>> concepts = provider.searchConcepts(es.getKey(), new ConceptQueryParams()
            .setCodeSystem(es.getKey())
            .setCodes(es.getValue().stream().map(CodeSystemEntityVersion::getCode).collect(Collectors.toList()))
            .all()).getData().stream().flatMap(c -> c.getVersions().stream()).collect(Collectors.groupingBy(CodeSystemEntityVersion::getCode));
        es.getValue().forEach(version -> {
          version.getDesignations().addAll(concepts.getOrDefault(version.getCode(), List.of()).stream()
              .findFirst().map(c -> Optional.ofNullable(c.getDesignations()).orElse(List.of()).stream().peek(d -> d.setSupplement(true)).toList()).orElse(List.of()));
          version.getPropertyValues().addAll(concepts.getOrDefault(version.getCode(), List.of()).stream()
              .findFirst().map(c -> Optional.ofNullable(c.getPropertyValues()).orElse(List.of()).stream().peek(p -> p.setSupplement(true)).toList()).orElse(List.of()));
        });
      });
    });
    return versions;
  }

  public void activate(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already activated, skipping activation process.", versionId);
      return;
    }
    repository.activate(currentVersion.getCodeSystem(), List.of(versionId));
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public void activate(String codeSystem, List<Long> versionIds) {
    long start = System.currentTimeMillis();
    repository.activate(codeSystem, versionIds);
    conceptRefreshViewJob.refreshView();
    log.info("Activated (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
  }

  @Transactional
  public void retire(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already retired, skipping retirement process.", versionId);
      return;
    }
    repository.retire(currentVersion.getCodeSystem(), List.of(versionId));
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public void retire(String codeSystem, List<Long> versionIds) {
    long start = System.currentTimeMillis();
    repository.retire(codeSystem, versionIds);
    conceptRefreshViewJob.refreshView();
    log.info("Retired (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
  }

  @Transactional
  public void saveAsDraft(Long versionId) {
    CodeSystemEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    if (PublicationStatus.draft.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already draft, skipping retirement process.", versionId);
      return;
    }
    repository.saveAsDraft(versionId);
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public CodeSystemEntityVersion duplicate(String codeSystem, Long id) {
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
    save(version, version.getCodeSystemEntityId());
    return version;
  }

  @Transactional
  public void cancel(Long id) {
    designationService.save(List.of(), id);
    entityPropertyValueService.save(List.of(), id);
    codeSystemAssociationService.cancel(id);

    repository.cancel(id);
    conceptRefreshViewJob.refreshView();
  }

  private void prepare(CodeSystemEntityVersion version) {
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
  }

  private void validate(CodeSystemEntityVersion version) {
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      throw ApiError.TE101.toApiException();
    }
  }
}
