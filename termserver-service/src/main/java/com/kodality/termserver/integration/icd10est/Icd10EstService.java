package com.kodality.termserver.integration.icd10est;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemService;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.CodeSystemVersionService;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.codesystem.association.AssociationTypeService;
import com.kodality.termserver.codesystem.concept.ConceptService;
import com.kodality.termserver.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.commons.client.BinaryHttpClient;
import com.kodality.termserver.commons.model.constant.Language;
import com.kodality.termserver.integration.common.ImportConfiguration;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est;
import com.kodality.termserver.integration.icd10est.utils.Extractor;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstZipReader;
import jakarta.inject.Singleton;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class Icd10EstService {
  private final BinaryHttpClient binaryHttpClient = new BinaryHttpClient();
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final AssociationTypeService associationTypeService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Transactional
  public List<Concept> importIcd10Est(String url, ImportConfiguration configuration) {
    prepareConfiguration(configuration);

    CodeSystemVersion version = prepareCodeSystemAndVersion(configuration);
    List<EntityProperty> properties = prepareProperties(configuration);
    prepareAssociations();

    List<Icd10Est> diagnoses = new Icd10EstZipReader().handleZipPack(getResource(url));
    List<Concept> concepts = Extractor.parseDiagnoses(diagnoses, configuration, properties);
    log.info("Creating concepts");
    concepts.forEach(concept -> {
      conceptService.save(concept, version.getCodeSystem());
      codeSystemEntityVersionService.save(concept.getVersions().get(0), concept.getId(), version.getCodeSystem());
    });
    log.info("Created '{}' concepts", concepts.size());
    codeSystemVersionService.saveEntityVersions(version.getId(), concepts.stream().map(c -> c.getVersions().get(0)).collect(Collectors.toList()));

    log.info("Activating version '{}' in codesystem '{}'", configuration.getCodeSystem(), configuration.getVersion());
    codeSystemVersionService.activateVersion(configuration.getCodeSystem(), configuration.getVersion());

    log.info("Import finished.");
    return concepts;
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return binaryHttpClient.GET(url).thenApply(HttpResponse::body).join();
  }

  private void prepareAssociations() {
    if (associationTypeService.load("is-a") == null) {
      AssociationType associationType = new AssociationType();
      associationType.setCode("is-a");
      associationType.setForwardName("Is a");
      associationType.setDirected(true);
      associationType.setAssociationKind("code-system-hierarchy");
      associationTypeService.save(associationType);
    }
  }

  private void prepareConfiguration(ImportConfiguration configuration) {
    configuration.setCodeSystem(configuration.getCodeSystem() == null ? Icd10EstConfiguration.codeSystem : configuration.getCodeSystem());
    configuration.setUri(configuration.getUri() == null ? Icd10EstConfiguration.uri : configuration.getUri());
    configuration.setVersion(configuration.getVersion() == null ? Icd10EstConfiguration.version : configuration.getVersion());
    configuration.setSource(configuration.getSource() == null ? Icd10EstConfiguration.source : configuration.getSource());
    configuration.setValidFrom(configuration.getValidFrom() == null ? LocalDate.now() : configuration.getValidFrom());
    configuration.setCodeSystemDescription(configuration.getCodeSystemDescription() == null ? Icd10EstConfiguration.codeSystemDescription : configuration.getCodeSystemDescription());
    configuration.setCodeSystemVersionDescription(configuration.getCodeSystemVersionDescription() == null ? Icd10EstConfiguration.codeSystemVersionDescription : configuration.getCodeSystemVersionDescription());
  }

  private CodeSystemVersion prepareCodeSystemAndVersion(ImportConfiguration configuration) {
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

  private List<EntityProperty> prepareProperties(ImportConfiguration configuration) {
    List<String> properties = List.of("term", "synonym", "notice", "include", "exclude");
    List<EntityProperty> existingProperties = entityPropertyService.query(
        new EntityPropertyQueryParams()
            .setNames(StringUtils.join(properties, ","))
            .setCodeSystem(configuration.getCodeSystem())).getData();
    return properties.stream().map(p -> {
      EntityProperty entityProperty = existingProperties.stream().filter(ep -> ep.getName().equals(p)).findFirst().orElse(new EntityProperty());
      entityProperty.setName(p);
      entityProperty.setStatus(PublicationStatus.active);
      entityPropertyService.save(entityProperty, configuration.getCodeSystem());
      return entityProperty;
    }).collect(Collectors.toList());
  }

  private void createCodeSystem(ImportConfiguration configuration) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(configuration.getCodeSystem());
    codeSystem.setUri(configuration.getUri());
    codeSystem.setNames(getCodeSystemName());
    codeSystem.setDescription(configuration.getCodeSystemDescription());
    codeSystemService.save(codeSystem);
  }

  private Map<String, String> getCodeSystemName() {
    Map<String, String> ln = new HashMap<>();
    ln.put(Language.et, "RHK-10");
    ln.put(Language.en, "ICD-10 Estonian Edition");
    return ln;
  }

  private CodeSystemVersion saveCodeSystemVersion(ImportConfiguration configuration, Long id) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setId(id);
    version.setCodeSystem(configuration.getCodeSystem());
    version.setVersion(configuration.getVersion());
    version.setSource(configuration.getSource());
    version.setPreferredLanguage(Language.et);
    version.setSupportedLanguages(List.of(Language.et, Language.en, Language.la));
    version.setDescription(configuration.getCodeSystemVersionDescription());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(configuration.getValidFrom());
    version.setExpirationDate(configuration.getValidTo());
    codeSystemVersionService.save(version);
    return version;
  }

}
