package com.kodality.termserver.common;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

  @Transactional
  public CodeSystemVersion prepareCodeSystemAndVersion(CodeSystem codeSystem) {
    log.info("Checking, the code system and version exists");
    Optional<CodeSystem> existingCodeSystem = codeSystemService.load(codeSystem.getId());
    if (existingCodeSystem.isEmpty()) {
      log.info("Code system {} does not exist, creating new", codeSystem.getId());
      codeSystemService.save(codeSystem);
    }

    CodeSystemVersion version = codeSystem.getVersions().get(0);
    Optional<CodeSystemVersion> existingVersion = codeSystemVersionService.load(version.getCodeSystem(), version.getVersion());
    if (existingVersion.isPresent() && existingVersion.get().getStatus().equals(PublicationStatus.active)) {
      throw ApiError.TE104.toApiException(Map.of("version", version.getVersion()));
    }
    log.info("Saving code system version {}", version.getVersion());
    version.setId(existingVersion.map(CodeSystemVersion::getId).orElse(null));
    codeSystemVersionService.save(version);
    return version;
  }

  public List<EntityProperty> prepareProperties(List<EntityProperty> properties, String codeSystem) {
    List<EntityProperty> existingProperties = entityPropertyService.query(new EntityPropertyQueryParams()
        .setNames(StringUtils.join(properties.stream().map(EntityProperty::getName), ","))
        .setCodeSystem(codeSystem)).getData();
    List<EntityProperty> entityProperties = new ArrayList<>(existingProperties);
    properties.forEach(p -> {
      Optional<EntityProperty> existing = existingProperties.stream().filter(ep -> ep.getName().equals(p.getName())).findFirst();
      if (existing.isEmpty()) {
        entityProperties.add(p);
      }
    });
    return entityPropertyService.save(entityProperties, codeSystem);
  }

  public EntityProperty prepareProperty(EntityProperty property, String codeSystem) {
    EntityPropertyQueryParams params = new EntityPropertyQueryParams();
    params.setNames(property.getName());
    params.setLimit(1);
    Optional<EntityProperty> existingProperty = entityPropertyService.query(params).findFirst();
    return existingProperty.orElseGet(() -> entityPropertyService.save(property, codeSystem));
  }

  public void prepareAssociationType(String code, String kind) {
    if (code == null) {
      return;
    }
    AssociationType associationType = new AssociationType();
    associationType.setCode(code);
    associationType.setDirected(true);
    associationType.setAssociationKind(kind);
    associationTypeService.save(associationType);
  }

  @Transactional
  public void importConcepts(List<Concept> concepts, CodeSystemVersion version, boolean activateVersion) {
    log.info("Creating '{}' concepts", concepts.size());
    concepts.forEach(concept -> {
      conceptService.save(concept, version.getCodeSystem());
      codeSystemEntityVersionService.save(concept.getVersions().get(0), concept.getId());
      codeSystemEntityVersionService.activate(concept.getVersions().get(0).getId());
    });
    log.info("Concepts created");

    log.info("Linking code system version and entity versions");
    codeSystemVersionService.saveEntityVersions(version.getId(), concepts.stream().map(c -> c.getVersions().get(0)).collect(Collectors.toList()));
    if (activateVersion) {
      codeSystemVersionService.activate(version.getCodeSystem(), version.getVersion());
    }

    log.info("Creating associations between code system entity versions");
    concepts.forEach(concept -> {
      List<CodeSystemAssociation> associations = prepareCodeSystemAssociations(concept.getVersions().get(0).getAssociations(), version.getId());
      codeSystemAssociationService.save(associations, concept.getVersions().get(0).getId());
    });

    log.info("Import finished.");
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
