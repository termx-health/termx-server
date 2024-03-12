package com.kodality.termx.terminology.terminology.codesystem;

import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.spacepackage.PackageVersion;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import com.kodality.termx.core.sys.spacepackage.resource.PackageResourceService;
import com.kodality.termx.core.sys.spacepackage.version.PackageVersionService;
import com.kodality.termx.terminology.terminology.association.AssociationTypeService;
import com.kodality.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.terminology.terminology.definedproperty.DefinedPropertyService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetImportService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemEntity;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemImportAction;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.property.DefinedProperty;
import com.kodality.termx.ts.property.DefinedPropertyQueryParams;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetImportAction;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.datatypes.Coding;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
    SessionStore.require().checkPermitted(codeSystem.getId(), Privilege.CS_EDIT);

    long start = System.currentTimeMillis();
    log.info("IMPORT STARTED : code system - {}", codeSystem.getId());

    associationTypeService.createIfNotExist(associationTypes);

    saveCodeSystem(codeSystem);
    CodeSystemVersion codeSystemVersion = codeSystem.getVersions().get(0);
    saveCodeSystemVersion(codeSystemVersion, action.isCleanRun());

    List<EntityProperty> entityProperties = saveProperties(codeSystem.getProperties(), codeSystem.getId());
    saveConcepts(prepareConcepts(codeSystem.getConcepts(), codeSystem.getBaseCodeSystem()), codeSystemVersion, entityProperties, action.isCleanConceptRun());

    if (action.isActivate()) {
      SessionStore.require().checkPermitted(codeSystem.getId(), Privilege.CS_PUBLISH);
      codeSystemVersionService.activate(codeSystem.getId(), codeSystemVersion.getVersion());
    }
    if (action.isRetire()) {
      SessionStore.require().checkPermitted(codeSystem.getId(), Privilege.CS_PUBLISH);
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
      List<String> codes = concepts.stream().map(Concept::getCode).collect(Collectors.toList());
      Map<String, Optional<Long>> baseVersionIds = conceptService.query(new ConceptQueryParams().setCodes(codes).setCodeSystem(baseCodeSystem).limit(concepts.size())).getData().stream()
              .collect(toMap(Concept::getCode, c -> c.getLastVersion().map(CodeSystemEntityVersion::getId)));
      concepts.forEach(c -> baseVersionIds.getOrDefault(c.getCode(), Optional.empty()).ifPresent(baseVersionId -> c.getVersions().get(0).setBaseEntityVersionId(baseVersionId)));
    }
    return concepts;
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
      log.info("Cancelling existing code system version {}", codeSystemVersion.getVersion());
      codeSystemVersionService.cancel(existingVersion.get().getId(), existingVersion.get().getCodeSystem());
    } else if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", codeSystemVersion.getVersion()));
    }
    log.info("Saving code system version {}", codeSystemVersion.getVersion());
    codeSystemVersionService.save(codeSystemVersion);
  }

  public List<EntityProperty> saveProperties(List<EntityProperty> properties, String codeSystem) {
    SessionStore.require().checkPermitted(codeSystem, Privilege.CS_EDIT);

    List<EntityProperty> existingProperties = entityPropertyService.query(new EntityPropertyQueryParams().setCodeSystem(codeSystem)).getData();
    List<EntityProperty> entityProperties = new ArrayList<>(existingProperties);
    entityProperties.addAll(properties.stream().filter(p -> existingProperties.stream().noneMatch(ep -> ep.getName().equals(p.getName()))).toList());
    return entityPropertyService.save(codeSystem, entityProperties);
  }

  public void saveConcepts(List<Concept> concepts, CodeSystemVersion version, List<EntityProperty> entityProperties, boolean cleanRun) {
    SessionStore.require().checkPermitted(version.getCodeSystem(), Privilege.CS_EDIT);

    log.info("Creating '{}' concepts", concepts.size());
    long start = System.currentTimeMillis();
    conceptService.batchSave(concepts, version.getCodeSystem());
    log.info("Concepts created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");


    log.info("Creating '{}' concept versions", concepts.size());
    start = System.currentTimeMillis();
    List<Long> activeConceptIds =
        concepts.stream().filter(c -> c.getVersions().get(0).getStatus() == null || PublicationStatus.active.equals(c.getVersions().get(0).getStatus()))
            .map(CodeSystemEntity::getId).toList();
    List<Long> retiredConceptIds =
        concepts.stream().filter(c -> PublicationStatus.retired.equals(c.getVersions().get(0).getStatus())).map(CodeSystemEntity::getId).toList();
    Map<Long, List<CodeSystemEntityVersion>> entityVersionMap = concepts.stream()
        .map(concept -> Pair.of(concept.getId(), prepareEntityVersion(concept.getVersions().get(0), entityProperties)))
        .collect(Collectors.toMap(Pair::getKey, p -> List.of(p.getValue())));
    if (!cleanRun) {
      mergeWithCurrentVersions(version.getId(), entityVersionMap);
    }
    codeSystemEntityVersionService.batchSave(entityVersionMap, version.getCodeSystem());
    log.info("Concept versions created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");

    log.info("Activating entity versions and linking them with code system version");
    start = System.currentTimeMillis();
    List<Long> entityVersionIds = concepts.stream().map(concept -> concept.getVersions().get(0).getId()).toList();
    List<Long> activeVersionIds =
        concepts.stream().filter(c -> activeConceptIds.contains(c.getId())).map(concept -> concept.getVersions().get(0).getId()).toList();
    List<Long> retiredVersionIds =
        concepts.stream().filter(c -> retiredConceptIds.contains(c.getId())).map(concept -> concept.getVersions().get(0).getId()).toList();
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

  private CodeSystemEntityVersion prepareEntityVersion(CodeSystemEntityVersion entityVersion, List<EntityProperty> properties) {
    entityVersion.setStatus(PublicationStatus.draft);
    if (CollectionUtils.isNotEmpty(entityVersion.getPropertyValues())) {
      entityVersion.setPropertyValues(prepareEntityVersionProperties(entityVersion.getPropertyValues(), properties));
    }
    if (CollectionUtils.isNotEmpty(entityVersion.getDesignations())) {
      entityVersion.setDesignations(prepareEntityVersionDesignations(entityVersion.getDesignations(), properties));
    }
    return entityVersion;
  }

  private List<EntityPropertyValue> prepareEntityVersionProperties(List<EntityPropertyValue> propertyValues, List<EntityProperty> properties) {
    propertyValues.forEach(pv -> {
      Optional<EntityProperty> property =
          properties.stream().filter(p -> p.getName().equals(pv.getEntityProperty()) || p.getId().equals(pv.getEntityPropertyId())).findFirst();
      if (property.isPresent()) {
        pv.setEntityPropertyId(property.get().getId());
        if (property.get().getType().equals(EntityPropertyType.coding)) {
          try {
            Coding coding = (Coding) pv.getValue();
            conceptService.load(coding.getSystem(), coding.getCode()).ifPresent(pv::setValue);
          } catch (RuntimeException ignored) {
          }
        }
      }
    });
    return propertyValues.stream().filter(pv -> pv.getEntityPropertyId() != null).collect(Collectors.toList());
  }

  private List<Designation> prepareEntityVersionDesignations(List<Designation> designations, List<EntityProperty> properties) {
    designations.forEach(d -> d.setDesignationTypeId(
        d.getDesignationTypeId() != null ? d.getDesignationTypeId() :
            d.getDesignationType() != null ?
                properties.stream().filter(p -> d.getDesignationType().equals(p.getName())).findFirst().map(EntityProperty::getId).orElse(null) : null));
    return designations.stream().filter(d -> d.getDesignationTypeId() != null).collect(Collectors.toList());
  }

  private void prepareCodeSystemAssociations(Map<Long, List<CodeSystemAssociation>> associations, Long versionId) {
    List<CodeSystemAssociation> codeSystemAssociations = associations.values().stream().flatMap(Collection::stream).toList();

    IntStream.range(0, (codeSystemAssociations.size() + 1000 - 1) / 1000)
        .mapToObj(i -> codeSystemAssociations.subList(i * 1000, Math.min(codeSystemAssociations.size(), (i + 1) * 1000))).forEach(batch -> {
          CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams()
              .setCode(batch.stream().map(CodeSystemAssociation::getTargetCode).filter(StringUtils::isNotEmpty).collect(Collectors.joining(",")))
              .setCodeSystemVersionId(versionId);
          params.setLimit(batch.size());
          List<CodeSystemEntityVersion> targets = codeSystemEntityVersionService.query(params).getData();
          batch.forEach(a -> a.setTargetId(a.getTargetCode() == null ? null :
              targets.stream().filter(t -> t.getCode().equals(a.getTargetCode())).findFirst().map(CodeSystemEntityVersion::getId).orElse(null)));
        });
    associations.keySet().forEach(k -> associations.put(k, associations.get(k).stream().filter(a -> a.getTargetId() != null).toList()));
  }


  private void mergeWithCurrentVersions(Long csVersionId, Map<Long, List<CodeSystemEntityVersion>> versions) {
    List<Long> entityIds = versions.keySet().stream().toList();
    IntStream.range(0, (entityIds.size() + 10000 - 1) / 10000)
        .mapToObj(i -> entityIds.subList(i * 10000, Math.min(versions.size(), (i + 1) * 10000))).forEach(batch -> {
          CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams()
              .setCodeSystemEntityIds(batch.stream().map(String::valueOf).collect(Collectors.joining(",")))
              .setStatus(String.join(",", PublicationStatus.active, PublicationStatus.draft))
              .all();
          Map<Long, List<CodeSystemEntityVersion>> existingVersions =
              codeSystemEntityVersionService.query(params).getData().stream().collect(Collectors.groupingBy(CodeSystemEntityVersion::getCodeSystemEntityId));
          existingVersions.keySet().forEach(entityId -> {
            Optional<CodeSystemEntityVersion> lastDraftVersion =
                existingVersions.get(entityId).stream().filter(v -> PublicationStatus.draft.equals(v.getStatus())).max(
                    Comparator.comparing(CodeSystemEntityVersion::getCreated));
            Optional<CodeSystemEntityVersion> lastActiveVersion =
                existingVersions.get(entityId).stream().filter(v -> PublicationStatus.active.equals(v.getStatus()))
                    .max(Comparator.comparing(CodeSystemEntityVersion::getCreated));
            if (lastDraftVersion.isPresent()) {
              versions.put(entityId, mergeWithDraftVersion(versions.get(entityId), lastDraftVersion.get()));
            } else if (lastActiveVersion.isPresent()) {
              versions.put(entityId, mergeWithActiveVersion(versions.get(entityId), lastActiveVersion.get(), csVersionId));
            }
          });
        });
  }

  private List<CodeSystemEntityVersion> mergeWithActiveVersion(List<CodeSystemEntityVersion> newVersions, CodeSystemEntityVersion activeVersion,
                                                               Long csVersionId) {
    codeSystemEntityVersionService.retire(activeVersion.getId());
    codeSystemVersionService.unlinkEntityVersions(csVersionId, List.of(activeVersion.getId()));
    return mergeVersions(newVersions, activeVersion);
  }

  private List<CodeSystemEntityVersion> mergeWithDraftVersion(List<CodeSystemEntityVersion> newVersions, CodeSystemEntityVersion draftVersion) {
    newVersions.forEach(v -> v.setId(draftVersion.getId()));
    return mergeVersions(newVersions, draftVersion);
  }

  private List<CodeSystemEntityVersion> mergeVersions(List<CodeSystemEntityVersion> targetVersions, CodeSystemEntityVersion sourceVersion) {
    targetVersions.forEach(v -> {
      v.setPropertyValues(Optional.ofNullable(v.getPropertyValues()).orElse(new ArrayList<>()));
      v.getPropertyValues().addAll(Optional.ofNullable(sourceVersion.getPropertyValues()).orElse(new ArrayList<>()).stream()
          .filter(pv -> v.getPropertyValues().stream().noneMatch(
              pv1 -> pv.getEntityPropertyId().equals(pv1.getEntityPropertyId()) && (JsonUtil.toJson(pv.getValue()).equals(JsonUtil.toJson(pv1.getValue())))))
          .toList());
      v.setDesignations(Optional.ofNullable(v.getDesignations()).orElse(new ArrayList<>()));
      v.getDesignations().addAll(Optional.ofNullable(sourceVersion.getDesignations()).orElse(new ArrayList<>()).stream()
          .filter(d -> v.getDesignations().stream().noneMatch(
              d1 -> d.getName().equals(d1.getName()) && d.getLanguage().equals(d1.getLanguage()) && d.getDesignationTypeId().equals(d1.getDesignationTypeId())))
          .toList());
      v.setAssociations(Optional.ofNullable(v.getAssociations()).orElse(new ArrayList<>()));
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
    return valueSet;
  }

  public static ValueSetVersion toValueSetVersion(CodeSystem codeSystem, List<String> properties) {
    CodeSystemVersion codeSystemVersion = codeSystem.getVersions().get(0);

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
        .setConceptVersionId(concept.getVersions().get(0).getId())
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
      packageResourceService.save(packageVersion.getId(), new PackageResource().setResourceType("code-system").setResourceId(codeSystemId));
    }
  }

}
