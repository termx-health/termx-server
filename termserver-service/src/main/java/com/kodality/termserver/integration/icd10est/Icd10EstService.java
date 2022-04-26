package com.kodality.termserver.integration.icd10est;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.Language;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.association.AssociationTypeService;
import com.kodality.termserver.integration.common.BinaryHttpClient;
import com.kodality.termserver.integration.common.ImportConfiguration;
import com.kodality.termserver.integration.common.TerminologyImportService;
import com.kodality.termserver.integration.icd10est.utils.Extractor;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstZipReader;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class Icd10EstService {
  private final TerminologyImportService importService;
  private final AssociationTypeService associationTypeService;
  private final BinaryHttpClient binaryHttpClient = new BinaryHttpClient();

  @Transactional
  public void importIcd10Est(String url, ImportConfiguration configuration) {
    prepareConfiguration(configuration);

    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(configuration);
    List<EntityProperty> properties = importService.prepareProperties(configuration, List.of("term", "synonym", "notice", "include", "exclude"));
    prepareAssociations();

    List<Icd10Est> diagnoses = new Icd10EstZipReader().handleZipPack(getResource(url));
    List<Concept> concepts = Extractor.parseDiagnoses(diagnoses, configuration, properties);
    importService.importConcepts(concepts, version, configuration);
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return binaryHttpClient.GET(url).body();
  }

  private void prepareAssociations() {
    AssociationType associationType = new AssociationType();
    associationType.setCode("is-a");
    associationType.setForwardName("Is a");
    associationType.setDirected(true);
    associationType.setAssociationKind("code-system-hierarchy");
    associationTypeService.save(associationType);
  }

  private void prepareConfiguration(ImportConfiguration configuration) {
    configuration.setUri(configuration.getUri() == null ? Icd10EstConfiguration.uri : configuration.getUri());
    configuration.setVersion(configuration.getVersion() == null ? Icd10EstConfiguration.version : configuration.getVersion());
    configuration.setSource(configuration.getSource() == null ? Icd10EstConfiguration.source : configuration.getSource());
    configuration.setValidFrom(configuration.getValidFrom() == null ? LocalDate.now() : configuration.getValidFrom());
    configuration.setCodeSystem(configuration.getCodeSystem() == null ? Icd10EstConfiguration.codeSystem : configuration.getCodeSystem());
    configuration.setCodeSystemName(configuration.getCodeSystemName() == null ? getCodeSystemName() : configuration.getCodeSystemName());
    configuration.setCodeSystemDescription(
        configuration.getCodeSystemDescription() == null ? Icd10EstConfiguration.codeSystemDescription : configuration.getCodeSystemDescription());
    configuration.setCodeSystemVersionDescription(configuration.getCodeSystemVersionDescription() == null ? Icd10EstConfiguration.codeSystemVersionDescription :
        configuration.getCodeSystemVersionDescription());
  }

  private LocalizedName getCodeSystemName() {
    Map<String, String> ln = new HashMap<>();
    ln.put(Language.et, "RHK-10");
    ln.put(Language.en, "ICD-10 Estonian Edition");
    return new LocalizedName(ln);
  }

}
