package com.kodality.termserver.terminology.codesystem;

import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.terminology.association.AssociationTypeService;
import com.kodality.termserver.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.zmei.fhir.datatypes.Coding;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Collection;
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

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemImportService {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final AssociationTypeService associationTypeService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemAssociationService codeSystemAssociationService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final UserPermissionService userPermissionService;

  @Transactional
  public void importCodeSystem(CodeSystem codeSystem, List<AssociationType> associationTypes, boolean activateVersion) {
    userPermissionService.checkPermitted(codeSystem.getId(), "CodeSystem", "edit");

    long start = System.currentTimeMillis();
    log.info("IMPORT STARTED : code system - {}", codeSystem.getId());

    associationTypes.forEach(associationTypeService::save);

    saveCodeSystem(codeSystem);
    CodeSystemVersion codeSystemVersion = codeSystem.getVersions().get(0);
    saveCodeSystemVersion(codeSystemVersion);

    List<EntityProperty> entityProperties = saveProperties(codeSystem.getProperties(), codeSystem.getId());
    saveConcepts(codeSystem.getConcepts(), codeSystemVersion, entityProperties);

    if (activateVersion) {
      codeSystemVersionService.activate(codeSystem.getId(), codeSystemVersion.getVersion());
    }

    log.info("IMPORT FINISHED (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
  }

  private void saveCodeSystem(CodeSystem codeSystem) {
    log.info("Saving code system");
    Optional<CodeSystem> existingCodeSystem = codeSystemService.load(codeSystem.getId());
    if (existingCodeSystem.isEmpty()) {
      log.info("Code system {} does not exist, creating new", codeSystem.getId());
      codeSystemService.save(codeSystem);
    }
  }

  private void saveCodeSystemVersion(CodeSystemVersion codeSystemVersion) {
    Optional<CodeSystemVersion> existingVersion = codeSystemVersionService.load(codeSystemVersion.getCodeSystem(), codeSystemVersion.getVersion());
    if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", codeSystemVersion.getVersion()));
    }
    existingVersion.ifPresent(v -> codeSystemVersionService.cancel(v.getId(), v.getCodeSystem()));
    log.info("Saving code system version {}", codeSystemVersion.getVersion());
    codeSystemVersionService.save(codeSystemVersion);
  }

  public List<EntityProperty> saveProperties(List<EntityProperty> properties, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    List<EntityProperty> existingProperties = entityPropertyService.query(new EntityPropertyQueryParams().setCodeSystem(codeSystem)).getData();
    List<EntityProperty> entityProperties = new ArrayList<>(existingProperties);
    entityProperties.addAll(properties.stream().filter(p -> existingProperties.stream().noneMatch(ep -> ep.getName().equals(p.getName()))).toList());
    return entityPropertyService.save(entityProperties, codeSystem);
  }

  public void saveConcepts(List<Concept> concepts, CodeSystemVersion version, List<EntityProperty> entityProperties) {
    userPermissionService.checkPermitted(version.getCodeSystem(), "CodeSystem", "edit");

    concepts = concepts.stream().collect(collectingAndThen(toCollection(() -> new TreeSet<>(comparing(Concept::getCode))), ArrayList::new)); //removes duplicate codes
    log.info("Creating '{}' concepts", concepts.size());
    long start = System.currentTimeMillis();
    conceptService.batchSave(concepts, version.getCodeSystem());
    log.info("Concepts created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");


    log.info("Creating '{}' concept versions", concepts.size());
    start = System.currentTimeMillis();
    Map<Long, CodeSystemEntityVersion> entityVersionMap = concepts.stream()
        .map(concept -> Pair.of(concept.getId(), prepareEntityVersion(concept.getVersions().get(0), entityProperties)))
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    codeSystemEntityVersionService.batchSave(entityVersionMap, version.getCodeSystem());
    log.info("Concept versions created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");

    log.info("Activating entity versions and linking them with code system version");
    start = System.currentTimeMillis();
    List<Long> entityVersionIds = concepts.stream().map(concept -> concept.getVersions().get(0).getId()).toList();
    codeSystemEntityVersionService.activate(entityVersionIds, version.getCodeSystem());
    codeSystemVersionService.linkEntityVersions(version.getId(), entityVersionIds);
    log.info("Linkage created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");

    log.info("Creating associations between code system entity versions");
    start = System.currentTimeMillis();
    Map<Long, List<CodeSystemAssociation>> associations = concepts.stream().peek(c -> {
      if (c.getVersions().get(0).getAssociations() == null) {
        c.getVersions().get(0).setAssociations(new ArrayList<>());
      }
    }).collect(Collectors.toMap(c -> c.getVersions().get(0).getId(), c -> c.getVersions().get(0).getAssociations()));
    prepareCodeSystemAssociations(associations, version.getId());
    codeSystemAssociationService.batchUpsert(associations, version.getCodeSystem());
    log.info("Associations created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
  }

  private CodeSystemEntityVersion prepareEntityVersion(CodeSystemEntityVersion entityVersion, List<EntityProperty> properties) {
    if (CollectionUtils.isNotEmpty(entityVersion.getPropertyValues())) {
      entityVersion.getPropertyValues().forEach(pv -> {
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
      entityVersion.setPropertyValues(entityVersion.getPropertyValues().stream().filter(pv -> pv.getEntityPropertyId() != null).collect(Collectors.toList()));
    }
    if (CollectionUtils.isNotEmpty(entityVersion.getDesignations())) {
      entityVersion.getDesignations().forEach(d -> d.setDesignationTypeId(
          d.getDesignationTypeId() != null ? d.getDesignationTypeId() :
              d.getDesignationType() != null ?
                  properties.stream().filter(p -> d.getDesignationType().equals(p.getName())).findFirst().map(EntityProperty::getId).orElse(null) : null));
      entityVersion.setDesignations(entityVersion.getDesignations().stream().filter(d -> d.getDesignationTypeId() != null).collect(Collectors.toList()));
    }
    return entityVersion;
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
  }
}
