package com.kodality.termserver.integration.common;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemService;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.CodeSystemVersionService;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.codesystem.concept.ConceptService;
import com.kodality.termserver.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.codesystem.entityproperty.EntityPropertyService;
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
public class TerminologyImportService {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemAssociationService codeSystemAssociationService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Transactional
  public CodeSystemVersion prepareCodeSystemAndVersion(ImportConfiguration configuration) {
    log.info("Checking, the code system and version exists");
    Optional<CodeSystem> codeSystem = codeSystemService.get(configuration.getCodeSystem());
    if (codeSystem.isEmpty()) {
      log.info("Code system {} does not exist, creating new", configuration.getCodeSystem());
      createCodeSystem(configuration);
    }

    Optional<CodeSystemVersion> version = codeSystemVersionService.getVersion(configuration.getCodeSystem(), configuration.getVersion());
    if (version.isPresent() && version.get().getStatus().equals(PublicationStatus.active)) {
      throw ApiError.TE105.toApiException(Map.of("version", configuration.getVersion()));
    }
    log.info("Saving code system version {}", configuration.getVersion());
    return saveCodeSystemVersion(configuration, version.map(CodeSystemVersion::getId).orElse(null));
  }

  private void createCodeSystem(ImportConfiguration configuration) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(configuration.getCodeSystem());
    codeSystem.setUri(configuration.getUri());
    codeSystem.setNames(configuration.getCodeSystemName());
    codeSystem.setDescription(configuration.getCodeSystemDescription());
    codeSystemService.create(codeSystem);
  }

  private CodeSystemVersion saveCodeSystemVersion(ImportConfiguration configuration, Long id) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setId(id);
    version.setCodeSystem(configuration.getCodeSystem());
    version.setVersion(configuration.getVersion());
    version.setSource(configuration.getSource());
    version.setPreferredLanguage(Language.en);
    version.setSupportedLanguages(List.of(Language.en));
    version.setDescription(configuration.getCodeSystemVersionDescription());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(configuration.getValidFrom());
    version.setExpirationDate(configuration.getValidTo());
    codeSystemVersionService.save(version);
    return version;
  }

  public List<EntityProperty> prepareProperties(ImportConfiguration configuration, List<String> properties) {
    List<EntityProperty> existingProperties = entityPropertyService.query(new EntityPropertyQueryParams()
        .setNames(StringUtils.join(properties, ","))
        .setCodeSystem(configuration.getCodeSystem())).getData();
    List<EntityProperty> entityProperties = new ArrayList<>(existingProperties);
    properties.forEach(p -> {
      Optional<EntityProperty> existing = existingProperties.stream().filter(ep -> ep.getName().equals(p)).findFirst();
      if (existing.isEmpty()) {
        entityProperties.add(new EntityProperty().setName(p).setStatus(PublicationStatus.active));
      }
    });
    return entityPropertyService.save(entityProperties, configuration.getCodeSystem());
  }

  @Transactional
  public void importConcepts(List<Concept> concepts, CodeSystemVersion version, ImportConfiguration configuration) {
    log.info("Creating '{}' concepts", concepts.size());
    concepts.forEach(concept -> {
      conceptService.save(concept, version.getCodeSystem());
      codeSystemEntityVersionService.save(concept.getVersions().get(0), concept.getId());
      codeSystemEntityVersionService.activate(concept.getVersions().get(0).getId());
    });
    log.info("Concepts created");

    log.info("Linking code system version and entity versions");
    codeSystemVersionService.saveEntityVersions(version.getId(), concepts.stream().map(c -> c.getVersions().get(0)).collect(Collectors.toList()));

    log.info("Creating associations between code system entity versions");
    concepts.forEach(concept -> {
      List<CodeSystemAssociation> associations = prepareCodeSystemAssociations(concept.getVersions().get(0).getAssociations(), version.getId());
      codeSystemAssociationService.save(associations, concept.getVersions().get(0).getId());
    });

    log.info("Activating version '{}' in code system '{}'", configuration.getCodeSystem(), configuration.getVersion());
    codeSystemVersionService.activate(configuration.getCodeSystem(), configuration.getVersion());

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
