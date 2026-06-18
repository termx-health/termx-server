package org.termx.terminology.terminology.codesystem;

import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import org.termx.terminology.ApiError;
import org.termx.terminology.Privilege;
import org.termx.core.auth.SessionStore;
import org.termx.sys.spacepackage.PackageVersion;
import org.termx.sys.spacepackage.PackageVersion.PackageResource;
import org.termx.core.sys.spacepackage.resource.PackageResourceService;
import org.termx.core.sys.spacepackage.version.PackageVersionService;
import org.termx.terminology.terminology.association.AssociationTypeService;
import org.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import org.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.terminology.terminology.definedproperty.DefinedPropertyService;
import org.termx.terminology.terminology.valueset.ValueSetImportService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.association.AssociationType;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemAssociation;
import org.termx.ts.codesystem.CodeSystemEntity;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemImportAction;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityProperty;
import org.termx.ts.codesystem.EntityPropertyQueryParams;
import org.termx.ts.codesystem.EntityPropertyType;
import org.termx.ts.codesystem.EntityPropertyValue;
import org.termx.ts.property.DefinedProperty;
import org.termx.ts.property.DefinedPropertyQueryParams;
import org.termx.ts.valueset.ValueSet;
import org.termx.ts.valueset.ValueSetImportAction;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import org.termx.ts.valueset.ValueSetVersionRuleSet;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import org.termx.ts.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.datatypes.Coding;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemImportService {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemRepository codeSystemRepository;
  private final EntityPropertyService entityPropertyService;
  private final AssociationTypeService associationTypeService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final DefinedPropertyService definedPropertyService;
  private final CodeSystemAssociationService codeSystemAssociationService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final PackageVersionService packageVersionService;
  private final PackageResourceService packageResourceService;

  private final ValueSetImportService valueSetImportService;

  @Transactional
  public CodeSystem importCodeSystem(CodeSystem codeSystem, List<AssociationType> associationTypes, CodeSystemImportAction action) {
    reconcileCanonicalId(codeSystem);
    SessionStore.require().checkPermitted(codeSystem.getId(), Privilege.CS_WRITE);

    long start = System.currentTimeMillis();
    log.info("IMPORT STARTED : code system - {}", codeSystem.getId());

    associationTypeService.createIfNotExist(associationTypes);

    saveCodeSystem(codeSystem);
    // (see reconcileCanonicalId — id may have been adopted from an existing same-uri code system)
    CodeSystemVersion codeSystemVersion = codeSystem.getVersions().getFirst();
    saveCodeSystemVersion(codeSystemVersion, action.isCleanRun());

    List<EntityProperty> entityProperties = saveProperties(codeSystem.getProperties(), codeSystem.getId());
    // Retire concepts absent from the import when reconciling the version to the file (clean-version)
    // or explicitly replacing concepts. Use replace-mode version handling only for replace-concepts on
    // a draft — clean-version reconciles the (reused) version in place with merge-mode handling, so a
    // changed active concept gets one new version (and the old is unlinked), not a duplicate.
    boolean retireRedundant = action.isCleanRun() || action.isCleanConceptRun();
    boolean replaceConceptVersions = action.isCleanConceptRun() && !action.isCleanRun();
    saveConcepts(prepareConcepts(codeSystem.getConcepts(), codeSystem.getBaseCodeSystem()), codeSystemVersion, entityProperties,
        replaceConceptVersions, retireRedundant);

    if (action.isActivate()) {
      SessionStore.require().checkPermitted(codeSystem.getId(), Privilege.CS_MAINTAIN);
      codeSystemVersionService.activate(codeSystem.getId(), codeSystemVersion.getVersion());
    }
    if (action.isRetire()) {
      SessionStore.require().checkPermitted(codeSystem.getId(), Privilege.CS_MAINTAIN);
      codeSystemVersionService.retire(codeSystem.getId(), codeSystemVersion.getVersion());
    }
    if (StringUtils.isNotEmpty(action.getSpaceToAdd())) {
      addToSpace(codeSystem.getId(), action.getSpaceToAdd());
    }

    if (action.isGenerateValueSet()) {
      generateValueSet(codeSystem, action);
    }

    log.info("IMPORT FINISHED (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
    return codeSystem;
  }

  private List<Concept> prepareConcepts(List<Concept> concepts, String baseCodeSystem) {
    //remove duplicate codes
    concepts = concepts.stream().collect(collectingAndThen(toCollection(() -> new TreeSet<>(comparing(Concept::getCode))), ArrayList::new));

    if (baseCodeSystem != null) {
      List<String> codes = concepts.stream().map(Concept::getCode).toList();
      Map<String, Optional<Long>> baseVersionIds = conceptService.query(new ConceptQueryParams().setCodes(codes).setCodeSystem(baseCodeSystem).limit(concepts.size())).getData().stream()
              .collect(toMap(Concept::getCode, c -> c.getLastVersion().map(CodeSystemEntityVersion::getId)));
      concepts.forEach(c -> baseVersionIds.getOrDefault(c.getCode(), Optional.empty()).ifPresent(baseVersionId -> c.getVersions().getFirst().setBaseEntityVersionId(baseVersionId)));
    }
    return concepts;
  }

  /**
   * Folds an incoming code system into an existing canonical: a FHIR resource's identity is its {@code url},
   * but the tx-ecosystem ships multiple versions of one canonical as SEPARATE resources sharing a url with
   * distinct ids. TermX keys on id and has a unique index on uri, so without this the second such resource
   * collides on {@code code_system_ukey}. When a code system with the same uri already exists under a
   * different id, adopt that id so the import becomes a NEW VERSION of the existing canonical.
   */
  private void reconcileCanonicalId(CodeSystem codeSystem) {
    if (StringUtils.isEmpty(codeSystem.getUri())) {
      return;
    }
    // (1) The same canonical (url) is already stored under a different id — adopt that id so this import
    // becomes a NEW VERSION of it (the tx-ecosystem ships versions of one canonical as separate resources).
    String byUri = codeSystemService.query(new org.termx.ts.codesystem.CodeSystemQueryParams().setUri(codeSystem.getUri()).limit(1)).findFirst()
        .map(CodeSystem::getId)
        .filter(existingId -> !existingId.equals(codeSystem.getId()))
        .orElse(null);
    if (byUri != null) {
      rekey(codeSystem, byUri, "Reconciling code system to existing canonical id");
      return;
    }
    // (2) Our id is already taken by a code system with a DIFFERENT url. A FHIR resource id is not the
    // canonical identity (the url is) — the tx-ecosystem deliberately reuses id "simple" for url "overload".
    // Keying on the resource id would fold this distinct canonical into the other one (phantom versions), so
    // re-key off our own url instead.
    CodeSystem clash = StringUtils.isEmpty(codeSystem.getId()) ? null : codeSystemService.load(codeSystem.getId()).orElse(null);
    if (clash != null && !codeSystem.getUri().equals(clash.getUri())) {
      String urlId = org.termx.core.fhir.BaseFhirMapper.fhirIdOrFromUrl(null, codeSystem.getUri());
      if (StringUtils.isNotEmpty(urlId) && !urlId.equals(codeSystem.getId())) {
        rekey(codeSystem, urlId, "Re-keying code system off url (id collides with a different canonical)");
      }
    }
  }

  /** Re-points a code system and all its nested versions/concepts/entity-versions at {@code newId}. */
  private void rekey(CodeSystem codeSystem, String newId, String why) {
    log.info("{}: '{}' -> '{}' (uri {})", why, codeSystem.getId(), newId, codeSystem.getUri());
    codeSystem.setId(newId);
    Optional.ofNullable(codeSystem.getVersions()).orElse(List.of()).forEach(v -> v.setCodeSystem(newId));
    Optional.ofNullable(codeSystem.getConcepts()).orElse(List.of()).forEach(concept -> {
      concept.setCodeSystem(newId);
      Optional.ofNullable(concept.getVersions()).orElse(List.of()).forEach(ev -> ev.setCodeSystem(newId));
    });
  }

  private void saveCodeSystem(CodeSystem codeSystem) {
    log.info("Saving code system");

    if (CollectionUtils.isNotEmpty(codeSystem.getProperties())) {
      Map<String, DefinedProperty> definedProperties = definedPropertyService.query(new DefinedPropertyQueryParams().limit(-1)).getData().stream()
          .collect(Collectors.toMap(p -> String.join(",", p.getName(), p.getType(), p.getKind()), p -> p));
      codeSystem.getProperties().forEach(p -> {
        DefinedProperty definedProperty = definedProperties.get(String.join(",", p.getName(), p.getType(), p.getKind()));
        if (definedProperty != null) {
          p.setUri(definedProperty.getUri());
          p.setDescription(definedProperty.getDescription());
          p.setDefinedEntityPropertyId(definedProperty.getId());
        }
      });
    }

    if (codeSystem.getBaseCodeSystemUri() != null && codeSystem.getBaseCodeSystem() == null) {
      Optional.ofNullable(codeSystemVersionService.loadLastVersionByUri(codeSystem.getBaseCodeSystemUri())).ifPresent(csv -> {
        codeSystem.setBaseCodeSystem(csv.getCodeSystem());
      });
    }

    Optional<CodeSystem> existingCodeSystem = codeSystemService.load(codeSystem.getId());
    if (existingCodeSystem.isEmpty()) {
      log.info("Code system {} does not exist, creating new", codeSystem.getId());
      codeSystemService.save(codeSystem);
    } else {
      log.info("Updating code system {}", codeSystem.getId());
      codeSystemRepository.save(codeSystem);
    }
  }

  private void saveCodeSystemVersion(CodeSystemVersion codeSystemVersion, boolean cleanRun) {
    Optional<CodeSystemVersion> existingVersion = codeSystemVersionService.load(codeSystemVersion.getCodeSystem(), codeSystemVersion.getVersion());

    if (cleanRun && existingVersion.isPresent()) {
      // Reconcile in place: reuse the existing version row instead of cancelling and recreating it.
      // Recreating gave every concept a brand-new entity version (churn); reusing lets saveConcepts
      // hold the unchanged ones. Concepts absent from the import are retired by saveConcepts.
      log.info("Reconciling existing code system version {} in place", codeSystemVersion.getVersion());
      codeSystemVersion.setId(existingVersion.get().getId());
    } else if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", codeSystemVersion.getVersion()));
    }

    if (codeSystemVersion.getBaseCodeSystem() != null && codeSystemVersion.getBaseCodeSystemVersion() != null && codeSystemVersion.getBaseCodeSystemVersion().getVersion() != null) {
      codeSystemVersionService.load(codeSystemVersion.getBaseCodeSystem(), codeSystemVersion.getBaseCodeSystemVersion().getVersion()).ifPresent(v -> {
        codeSystemVersion.getBaseCodeSystemVersion().setId(v.getId());
      });
    }

    if (codeSystemVersion.getBaseCodeSystemUri() != null && codeSystemVersion.getBaseCodeSystemVersion() != null && codeSystemVersion.getBaseCodeSystemVersion().getVersion() != null) {
      codeSystemVersionService.loadVersionByUri(codeSystemVersion.getBaseCodeSystemUri(), codeSystemVersion.getBaseCodeSystemVersion().getVersion()).ifPresent(v -> {
        codeSystemVersion.getBaseCodeSystemVersion().setId(v.getId());
      });
    }

    log.info("Saving code system version {}", codeSystemVersion.getVersion());
    codeSystemVersionService.save(codeSystemVersion);
  }

  public List<EntityProperty> saveProperties(List<EntityProperty> properties, String codeSystem) {
    SessionStore.require().checkPermitted(codeSystem, Privilege.CS_WRITE);

    List<EntityProperty> existingProperties = entityPropertyService.query(new EntityPropertyQueryParams().setCodeSystem(codeSystem)).getData();
    List<EntityProperty> entityProperties = new ArrayList<>(existingProperties);
    entityProperties.addAll(properties.stream().filter(p -> existingProperties.stream().noneMatch(ep -> ep.getName().equals(p.getName()))).toList());
    return entityPropertyService.save(codeSystem, entityProperties);
  }

  public void saveConcepts(List<Concept> concepts, CodeSystemVersion version, List<EntityProperty> entityProperties,
                           boolean replaceConceptVersions, boolean retireRedundant) {
    SessionStore.require().checkPermitted(version.getCodeSystem(), Privilege.CS_WRITE);

    log.info("Creating '{}' concepts", concepts.size());
    long start = System.currentTimeMillis();
    conceptService.batchSave(concepts, version.getCodeSystem());
    log.info("Concepts created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");

    // Reconcile the version to exactly the imported set: retire concepts that are no longer present
    // (clean-version or replace-concepts). Held concepts keep their version; this only touches the
    // ones absent from the import.
    if (retireRedundant) {
      log.info("Retiring concepts absent from the import");
      conceptService.cancelOrRetireRedundantConcepts(concepts, version);
    }

    log.info("Creating '{}' concept versions", concepts.size());
    start = System.currentTimeMillis();
    List<Long> activeConceptIds =
        concepts.stream().filter(c -> c.getVersions().getFirst().getStatus() == null || PublicationStatus.active.equals(c.getVersions().getFirst().getStatus()))
            .map(CodeSystemEntity::getId).toList();
    List<Long> retiredConceptIds =
        concepts.stream().filter(c -> PublicationStatus.retired.equals(c.getVersions().getFirst().getStatus())).map(CodeSystemEntity::getId).toList();

    Map<Long, List<CodeSystemEntityVersion>> entityVersionMap = new HashMap<>();
    final Map<String, Optional<Concept>> conceptCache = new HashMap<>();
    for (CodeSystemEntity concept : concepts) {
      var partialVersion = prepareEntityVersion(concept.getVersions().getFirst(), entityProperties, conceptCache);
      entityVersionMap.put(concept.getId(), List.of(partialVersion));
    }
    Map<Long, EntityProperty> propertiesById = entityProperties.stream()
        .filter(p -> p.getId() != null).collect(Collectors.toMap(EntityProperty::getId, p -> p, (a, b) -> a));
    holdUnchangedAndMerge(version, concepts, entityVersionMap, propertiesById, retiredConceptIds, replaceConceptVersions);
    codeSystemEntityVersionService.batchSave(entityVersionMap, version.getCodeSystem());
    log.info("Concept versions created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");

    log.info("Activating entity versions and linking them with code system version");
    start = System.currentTimeMillis();
    List<Long> entityVersionIds = concepts.stream().map(concept -> concept.getVersions().getFirst().getId()).toList();
    List<Long> activeVersionIds =
        concepts.stream().filter(c -> activeConceptIds.contains(c.getId())).map(concept -> concept.getVersions().getFirst().getId()).toList();
    List<Long> retiredVersionIds =
        concepts.stream().filter(c -> retiredConceptIds.contains(c.getId())).map(concept -> concept.getVersions().getFirst().getId()).toList();
    codeSystemEntityVersionService.activate(version.getCodeSystem(), activeVersionIds);
    codeSystemEntityVersionService.retire(version.getCodeSystem(), retiredVersionIds);
    codeSystemVersionService.linkEntityVersions(version.getId(), entityVersionIds);
    log.info("Linkage created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");

    log.info("Creating associations between code system entity versions");
    start = System.currentTimeMillis();
    Map<Long, List<CodeSystemAssociation>> associations = entityVersionMap.values().stream().flatMap(Collection::stream)
        .collect(Collectors.toMap(CodeSystemEntityVersion::getId, CodeSystemEntityVersion::getAssociations));
    prepareCodeSystemAssociations(associations, version.getId());
    codeSystemAssociationService.batchUpsert(associations, version.getCodeSystem());
    log.info("Associations created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
  }

  private CodeSystemVersion getCurrentCodeSystemVersion(CodeSystemVersion version) {
    if (version.getVersion().contains("_shadow")) {
      return codeSystemVersionService.load(version.getCodeSystem(), version.getVersion().replace("_shadow", ""))
              .orElse(version);
    }
    return version;
  }

  private CodeSystemEntityVersion prepareEntityVersion(CodeSystemEntityVersion entityVersion, List<EntityProperty> properties, Map<String, Optional<Concept>> conceptCache) {
    entityVersion.setStatus(PublicationStatus.draft);
    if (CollectionUtils.isNotEmpty(entityVersion.getPropertyValues())) {
      entityVersion.setPropertyValues(prepareEntityVersionProperties(entityVersion.getPropertyValues(), properties, conceptCache));
    }
    if (CollectionUtils.isNotEmpty(entityVersion.getDesignations())) {
      entityVersion.setDesignations(prepareEntityVersionDesignations(entityVersion.getDesignations(), properties));
    }
    return entityVersion;
  }

  private List<EntityPropertyValue> prepareEntityVersionProperties(List<EntityPropertyValue> propertyValues, List<EntityProperty> properties, Map<String, Optional<Concept>> conceptCache) {
    propertyValues.forEach(pv -> {
      Optional<EntityProperty> property =
          properties.stream().filter(p -> p.getName().equals(pv.getEntityProperty()) || p.getId().equals(pv.getEntityPropertyId())).findFirst();
      if (property.isPresent()) {
        pv.setEntityPropertyId(property.get().getId());
        if (property.get().getType().equals(EntityPropertyType.coding)) {
          try {
            // CodeSystemFhirMapper normalises imported FHIR Codings to
            // EntityPropertyValueCodingValue at the boundary; FileImporter and
            // similar paths may still hand us a raw FHIR Coding, so accept both.
            // Whichever shape we receive, store as EPVCV — the canonical TermX
            // shape that preserves `display` through later round-trips (a bare
            // Concept doesn't carry display, so the previous Concept-replacement
            // was lossy).
            EntityPropertyValue.EntityPropertyValueCodingValue coding;
            if (pv.getValue() instanceof Coding c) {
              coding = new EntityPropertyValue.EntityPropertyValueCodingValue(c.getCode(), c.getSystem());
              coding.setDisplay(c.getDisplay());
              coding.setVersion(c.getVersion());
            } else {
              coding = pv.asCodingValue();
            }
            // Resolution: if the Coding refers to a stored TermX concept, fine —
            // the lookup is still useful for callers that want the internal id,
            // but we don't replace the stored value (which would drop display).
            // Unresolvable Codings (e.g. external systems we don't host) round-
            // trip verbatim instead of being silently dropped on export.
            if (coding != null && coding.getCodeSystem() != null && coding.getCode() != null) {
              String key = coding.getCodeSystem() + "|" + coding.getCode();
              conceptCache.computeIfAbsent(key, k ->
                  conceptService.load(coding.getCodeSystem(), coding.getCode())
                      .or(() -> conceptService.loadByUri(coding.getCodeSystem(), coding.getCode()))
              );
            }
            pv.setValue(coding);
          } catch (RuntimeException ignored) {
          }
        }
      }
    });
    return propertyValues.stream().filter(pv -> pv.getEntityPropertyId() != null).toList();
  }

  private List<Designation> prepareEntityVersionDesignations(List<Designation> designations, List<EntityProperty> properties) {
    designations.forEach(d -> d.setDesignationTypeId(
        d.getDesignationTypeId() != null ? d.getDesignationTypeId() :
            d.getDesignationType() != null ?
                properties.stream().filter(p -> d.getDesignationType().equals(p.getName())).findFirst().map(EntityProperty::getId).orElse(null) : null));
    return designations.stream().filter(d -> d.getDesignationTypeId() != null).toList();
  }

  private void prepareCodeSystemAssociations(Map<Long, List<CodeSystemAssociation>> associations, Long versionId) {
    final Map<String, Long> codeToId = codeSystemEntityVersionService.findCodeToIdMap(versionId);
    associations.values().stream()
        .flatMap(Collection::stream)
        .filter(a -> a.getTargetId() == null && a.getTargetCode() != null)
        .forEach(a -> a.setTargetId(codeToId.get(a.getTargetCode())));
    associations.keySet().forEach(k -> associations.put(k, associations.get(k).stream().filter(a -> a.getTargetId() != null).toList()));
  }


  /**
   * Decides, per concept, whether the imported version differs from the one already stored. Concepts
   * whose content is identical (see {@link ConceptContentSignature}) are HELD — the existing version
   * is reused (no unlink, no new version, no churn); only its status is (re)applied later. Changed or
   * new concepts are processed per mode:
   * <ul>
   *   <li>merge — existing draft is updated in place, existing active gets a new version;</li>
   *   <li>replace — existing drafts of changed/new concepts are cancelled (held ones are left alone).</li>
   * </ul>
   * Held concepts are removed from {@code entityVersionMap} so {@code batchSave} skips them; their id
   * is copied onto the prepared version so the later activate/retire/link steps target the kept row.
   *
   * <p>Retiring is treated as a change: an active concept being retired gets a NEW (retired) version
   * (the active one is preserved as history), exactly like a content change on an active concept.
   */
  private void holdUnchangedAndMerge(CodeSystemVersion version, List<Concept> concepts,
                                     Map<Long, List<CodeSystemEntityVersion>> entityVersionMap,
                                     Map<Long, EntityProperty> propertiesById, List<Long> retiredEntityIds, boolean cleanRun) {
    Long csVersionId = getCurrentCodeSystemVersion(version).getId();
    Map<Long, CodeSystemEntityVersion> existing = loadExistingVersions(csVersionId, entityVersionMap.keySet());
    java.util.Set<Long> changedEntityIds = new java.util.HashSet<>();   // Set, not List: O(1) contains for the replace filter below

    for (Long entityId : new ArrayList<>(entityVersionMap.keySet())) {
      CodeSystemEntityVersion prepared = entityVersionMap.get(entityId).getFirst();
      CodeSystemEntityVersion existingVersion = existing.get(entityId);

      // Retiring an active concept must create a new version (don't flip the active one in place).
      boolean retireTransition = existingVersion != null
          && retiredEntityIds.contains(entityId)
          && PublicationStatus.active.equals(existingVersion.getStatus());

      if (existingVersion != null && !retireTransition
          && ConceptContentSignature.sameContent(prepared, existingVersion, propertiesById)) {
        // Unchanged: keep the stored version. Reuse its id so activate/retire/link target it, and
        // drop it from the batch so no new/duplicate version is written.
        prepared.setId(existingVersion.getId());
        prepared.setStatus(existingVersion.getStatus());
        entityVersionMap.remove(entityId);
        continue;
      }

      changedEntityIds.add(entityId);
      if (!cleanRun && existingVersion != null) {
        if (PublicationStatus.draft.equals(existingVersion.getStatus())) {
          entityVersionMap.put(entityId, mergeWithDraftVersion(entityVersionMap.get(entityId), existingVersion));
        } else if (PublicationStatus.active.equals(existingVersion.getStatus())) {
          entityVersionMap.put(entityId, mergeWithActiveVersion(entityVersionMap.get(entityId), existingVersion, csVersionId));
        }
      }
    }

    if (cleanRun) {
      // Replace: cancel drafts only for changed/new concepts — held concepts keep their version.
      List<Concept> changed = concepts.stream().filter(c -> changedEntityIds.contains(c.getId())).toList();
      if (!changed.isEmpty()) {
        codeSystemEntityVersionService.cancelAllDraftVersions(version, changed);
      }
    }
  }

  /** Existing version of each concept on the current code system version, preferring the draft, else the active one. */
  private Map<Long, CodeSystemEntityVersion> loadExistingVersions(Long csVersionId, java.util.Set<Long> entityIds) {
    Map<Long, CodeSystemEntityVersion> result = new HashMap<>();
    List<Long> ids = new ArrayList<>(entityIds);
    IntStream.range(0, (ids.size() + 10000 - 1) / 10000)
        .mapToObj(i -> ids.subList(i * 10000, Math.min(ids.size(), (i + 1) * 10000))).forEach(batch -> {
          CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams()
              .setCodeSystemEntityIds(batch.stream().map(String::valueOf).collect(Collectors.joining(",")))
              .setCodeSystemVersionId(csVersionId)
              .setStatus(String.join(",", PublicationStatus.active, PublicationStatus.draft, PublicationStatus.retired))
              .all();
          Map<Long, List<CodeSystemEntityVersion>> grouped =
              codeSystemEntityVersionService.query(params).getData().stream().collect(Collectors.groupingBy(CodeSystemEntityVersion::getCodeSystemEntityId));
          grouped.forEach((entityId, versions) -> {
            Optional<CodeSystemEntityVersion> draft = versions.stream().filter(v -> PublicationStatus.draft.equals(v.getStatus()))
                .max(Comparator.comparing(CodeSystemEntityVersion::getCreated));
            Optional<CodeSystemEntityVersion> active = versions.stream().filter(v -> PublicationStatus.active.equals(v.getStatus()))
                .max(Comparator.comparing(CodeSystemEntityVersion::getCreated));
            Optional<CodeSystemEntityVersion> retired = versions.stream().filter(v -> PublicationStatus.retired.equals(v.getStatus()))
                .max(Comparator.comparing(CodeSystemEntityVersion::getCreated));
            draft.or(() -> active).or(() -> retired).ifPresent(v -> result.put(entityId, v));
          });
        });
    return result;
  }

  private List<CodeSystemEntityVersion> mergeWithActiveVersion(List<CodeSystemEntityVersion> newVersions, CodeSystemEntityVersion activeVersion,
                                                               Long csVersionId) {
    codeSystemVersionService.unlinkEntityVersions(csVersionId, List.of(activeVersion.getId()));
    return mergeVersions(newVersions, activeVersion);
  }

  private List<CodeSystemEntityVersion> mergeWithDraftVersion(List<CodeSystemEntityVersion> newVersions, CodeSystemEntityVersion draftVersion) {
    newVersions.forEach(v -> v.setId(draftVersion.getId()));
    return mergeVersions(newVersions, draftVersion);
  }

  private List<CodeSystemEntityVersion> mergeVersions(List<CodeSystemEntityVersion> targetVersions, CodeSystemEntityVersion sourceVersion) {
    targetVersions.forEach(v -> {
      // Wrap in a mutable ArrayList: prepareEntityVersion* produce immutable Stream.toList() lists,
      // so addAll(...) below would otherwise throw UnsupportedOperationException.
      v.setPropertyValues(new ArrayList<>(Optional.ofNullable(v.getPropertyValues()).orElse(List.of())));
      v.getPropertyValues().addAll(Optional.ofNullable(sourceVersion.getPropertyValues()).orElse(new ArrayList<>()).stream()
          .filter(pv -> v.getPropertyValues().stream().noneMatch(
              pv1 -> Objects.equals(pv.getEntityPropertyId(), pv1.getEntityPropertyId()) &&
                  Objects.equals(JsonUtil.toJson(pv.getValue()), JsonUtil.toJson(pv1.getValue()))))
          .toList());
      v.setDesignations(new ArrayList<>(Optional.ofNullable(v.getDesignations()).orElse(List.of())));
      v.getDesignations().addAll(Optional.ofNullable(sourceVersion.getDesignations()).orElse(new ArrayList<>()).stream()
          .filter(d -> v.getDesignations().stream().noneMatch(
              d1 -> Objects.equals(d.getName(), d1.getName()) &&
                  Objects.equals(d.getLanguage(), d1.getLanguage()) &&
                  Objects.equals(d.getDesignationTypeId(), d1.getDesignationTypeId())))
          .toList());
      v.setAssociations(new ArrayList<>(Optional.ofNullable(v.getAssociations()).orElse(List.of())));
      v.getAssociations().addAll(Optional.ofNullable(sourceVersion.getAssociations()).orElse(new ArrayList<>()).stream()
          .filter(a -> v.getAssociations().stream()
              .noneMatch(a1 -> a.getAssociationType().equals(a1.getAssociationType()) && a.getTargetCode().equals(a1.getTargetCode()))).toList());
    });
    return targetVersions;
  }

  // VS
  private void generateValueSet(CodeSystem codeSystem, CodeSystemImportAction csAction) {
    ValueSet valueSet = toValueSet(codeSystem, csAction.getValueSetProperties());
    ValueSetImportAction action = new ValueSetImportAction()
        .setActivate(csAction.isActivate())
        .setRetire(csAction.isRetire())
        .setSpaceToAdd(csAction.getSpaceToAdd())
        .setCleanRun(csAction.isCleanRun());
    valueSetImportService.importValueSet(valueSet, action);
  }

  public static ValueSet toValueSet(CodeSystem codeSystem, List<String> properties) {
    ValueSet valueSet = new ValueSet();
    valueSet.setId(codeSystem.getId());
    valueSet.setUri(codeSystem.getUri().replaceAll("(?i)CodeSystem", "ValueSet"));
    valueSet.setTitle(codeSystem.getTitle());
    valueSet.setName(codeSystem.getName());
    valueSet.setDescription(codeSystem.getDescription());
    valueSet.setVersions(List.of(toValueSetVersion(codeSystem, properties)));
    valueSet.setContacts(codeSystem.getContacts());
    valueSet.setIdentifiers(codeSystem.getIdentifiers());
    valueSet.setPublisher(codeSystem.getPublisher());
    valueSet.setCopyright(codeSystem.getCopyright());
    valueSet.setPermissions(codeSystem.getPermissions());
    valueSet.setExternalWebSource(codeSystem.getExternalWebSource());
    return valueSet;
  }

  public static ValueSetVersion toValueSetVersion(CodeSystem codeSystem, List<String> properties) {
    CodeSystemVersion codeSystemVersion = codeSystem.getVersions().getFirst();

    ValueSetVersion version = new ValueSetVersion();
    version.setValueSet(codeSystem.getId());
    version.setVersion(codeSystemVersion.getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setSupportedLanguages(codeSystemVersion.getSupportedLanguages());
    version.setPreferredLanguage(codeSystemVersion.getPreferredLanguage());
    version.setReleaseDate(codeSystemVersion.getReleaseDate());
    version.setIdentifiers(codeSystemVersion.getIdentifiers());
    version.setRuleSet(new ValueSetVersionRuleSet().setInactive(false).setRules(List.of(
        new ValueSetVersionRule()
            .setType(ValueSetVersionRuleType.include)
            .setProperties(properties)
            .setCodeSystem(codeSystemVersion.getCodeSystem())
            .setCodeSystemVersion(codeSystemVersion)
            .setConcepts(Optional.ofNullable(codeSystem.getBaseCodeSystem()).isPresent() ?
                codeSystem.getConcepts().stream().map(CodeSystemImportService::toValueSetConcept).toList() : null)
    )));
    return version;
  }

  private static ValueSetVersionConcept toValueSetConcept(Concept concept) {
    ValueSetVersionConcept vsConcept = new ValueSetVersionConcept();
    vsConcept.setConcept(new ValueSetVersionConceptValue()
        .setConceptVersionId(concept.getVersions().getFirst().getId())
        .setCode(concept.getCode())
        .setCodeSystem(concept.getCodeSystem()));
    return vsConcept;
  }

  private void addToSpace(String codeSystemId, String spaceToAdd) {
    String[] spaceAndPackage = PipeUtil.parsePipe(spaceToAdd);
    PackageVersion packageVersion = packageVersionService.loadLastVersion(spaceAndPackage[0], spaceAndPackage[1]);
    if (packageVersion == null) {
      throw ApiError.TE112.toApiException(Map.of("space", spaceAndPackage[0], "package", spaceAndPackage[1]));
    }

    List<PackageResource> resources = packageVersion.getResources() == null ? List.of() : packageVersion.getResources();
    boolean exists = resources.stream().anyMatch(r -> r.getResourceType().equals("code-system") && r.getResourceId().equals(codeSystemId));
    if (!exists) {
      PackageResource pr = new PackageResource();
      pr.setResourceType("code-system");
      pr.setResourceId(codeSystemId);
      packageResourceService.save(packageVersion.getId(), pr);
    }
  }

}
