package com.kodality.termserver.common;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.ts.association.AssociationTypeService;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionService;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.ts.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

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

    log.info("Creating '{}' concepts", concepts.size());
    concepts.forEach(concept -> {
      conceptService.save(concept, version.getCodeSystem());

      CodeSystemEntityVersion entityVersion = prepareEntityVersion(concept.getVersions().get(0), entityProperties);
      codeSystemEntityVersionService.save(entityVersion, concept.getId());
      codeSystemEntityVersionService.activate(entityVersion.getId());
    });
    log.info("Concepts created");

    log.info("Linking code system version and entity versions");
    codeSystemVersionService.saveEntityVersions(version.getId(), concepts.stream().map(c -> c.getVersions().get(0)).collect(Collectors.toList()));

    log.info("Creating associations between code system entity versions");
    concepts.forEach(concept -> {
      List<CodeSystemAssociation> associations = prepareCodeSystemAssociations(concept.getVersions().get(0).getAssociations(), version.getId());
      codeSystemAssociationService.save(associations, concept.getVersions().get(0).getId(), version.getCodeSystem());
    });

    log.info("Import finished.");
  }

  private CodeSystemEntityVersion prepareEntityVersion(CodeSystemEntityVersion entityVersion, List<EntityProperty> properties) {
    if (CollectionUtils.isNotEmpty(entityVersion.getPropertyValues())) {
      entityVersion.getPropertyValues().forEach(pv -> pv.setEntityPropertyId(pv.getEntityPropertyId() != null ? pv.getEntityPropertyId() :
          pv.getEntityProperty() != null ? properties.stream().filter(p -> pv.getEntityProperty().equals(p.getName())).findFirst().map(EntityProperty::getId).orElse(null) : null));
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
