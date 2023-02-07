package com.kodality.termserver.common;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.codesystem.EntityPropertyType;
import com.kodality.termserver.ts.association.AssociationTypeService;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionService;
import com.kodality.termserver.ts.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.zmei.fhir.datatypes.Coding;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    associationTypes.forEach(associationTypeService::save);

    saveCodeSystem(codeSystem);
    CodeSystemVersion codeSystemVersion = codeSystem.getVersions().get(0);
    saveCodeSystemVersion(codeSystemVersion);

    List<EntityProperty> entityProperties = saveProperties(codeSystem.getProperties(), codeSystem.getId());
    saveConcepts(codeSystem.getConcepts(), codeSystemVersion, entityProperties);

    if (activateVersion) {
      codeSystemVersionService.activate(codeSystem.getId(), codeSystemVersion.getVersion());
    }
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
    log.info("Saving code system version {}", codeSystemVersion.getVersion());
    codeSystemVersion.setId(existingVersion.map(CodeSystemVersion::getId).orElse(null));
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
    conceptService.batchSave(concepts, version.getCodeSystem());
    log.info("Concepts created");

    log.info("Creating '{}' concept versions", concepts.size());
    concepts.forEach(concept -> {
      CodeSystemEntityVersion entityVersion = prepareEntityVersion(concept.getVersions().get(0), entityProperties);
      codeSystemEntityVersionService.save(entityVersion, concept.getId());
    });
    log.info("Concept versions created");

    log.info("Activating entity versions and linking them with code system version");
    List<Long> entityVersionIds = concepts.stream().map(concept -> concept.getVersions().get(0).getId()).toList();
    IntStream.range(0,(entityVersionIds.size()+1000-1)/1000).mapToObj(i -> entityVersionIds.subList(i*1000, Math.min(entityVersionIds.size(), (i+1)*1000))).forEach(batch ->{
      codeSystemEntityVersionService.activate(batch);
      codeSystemVersionService.linkEntityVersions(version.getId(), batch);
    });
    log.info("Linkage created");

    log.info("Creating associations between code system entity versions");
    concepts.forEach(concept -> {
      List<CodeSystemAssociation> associations = prepareCodeSystemAssociations(concept.getVersions().get(0).getAssociations(), version.getId());
      codeSystemAssociationService.save(associations, concept.getVersions().get(0).getId(), version.getCodeSystem());
    });
    log.info("Associations created");

    log.info("Import finished.");
  }

  private CodeSystemEntityVersion prepareEntityVersion(CodeSystemEntityVersion entityVersion, List<EntityProperty> properties) {
    if (CollectionUtils.isNotEmpty(entityVersion.getPropertyValues())) {
      entityVersion.getPropertyValues().forEach(pv -> {
        Optional<EntityProperty> property = properties.stream().filter(p -> p.getName().equals(pv.getEntityProperty()) || p.getId().equals(pv.getEntityPropertyId())).findFirst();
        if (property.isPresent()) {
          pv.setEntityPropertyId(property.get().getId());
          if (property.get().getType().equals(EntityPropertyType.coding)) {
            try {
              Coding coding = (Coding) pv.getValue();
              conceptService.load(coding.getSystem(), coding.getCode()).ifPresent(pv::setValue);
            } catch (RuntimeException ignored) {}
          }
        }
      });
      entityVersion.setPropertyValues(entityVersion.getPropertyValues().stream().filter(pv -> pv.getEntityPropertyId() != null).collect(Collectors.toList()));
    }
    if (CollectionUtils.isNotEmpty(entityVersion.getDesignations())) {
      entityVersion.getDesignations().forEach(d -> d.setDesignationTypeId(
          d.getDesignationTypeId() != null ? d.getDesignationTypeId() :
              d.getDesignationType() != null ? properties.stream().filter(p -> d.getDesignationType().equals(p.getName())).findFirst().map(EntityProperty::getId).orElse(null) : null));
      entityVersion.setDesignations(entityVersion.getDesignations().stream().filter(d -> d.getDesignationTypeId() != null).collect(Collectors.toList()));
    }
    return entityVersion;
  }

  private List<CodeSystemAssociation> prepareCodeSystemAssociations(List<CodeSystemAssociation> associations, Long versionId) {
    if (associations == null) {
      return new ArrayList<>();
    }
    associations.forEach(a -> {
      if (a.getTargetCode() != null) {
        Long targetId = codeSystemEntityVersionService.query(new CodeSystemEntityVersionQueryParams()
            .setCode(a.getTargetCode())
            .setCodeSystemVersionId(versionId)).findFirst().map(CodeSystemEntityVersion::getId).orElse(null);
        a.setTargetId(targetId);
      }
    });
    return associations;
  }
}
