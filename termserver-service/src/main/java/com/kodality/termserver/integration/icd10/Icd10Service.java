package com.kodality.termserver.integration.icd10;


import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.association.AssociationTypeService;
import com.kodality.termserver.commons.client.BinaryHttpClient;
import com.kodality.termserver.commons.model.constant.Language;
import com.kodality.termserver.commons.model.model.LocalizedName;
import com.kodality.termserver.integration.common.ImportConfiguration;
import com.kodality.termserver.integration.common.TerminologyImportService;
import com.kodality.termserver.integration.icd10.utils.Icd10;
import com.kodality.termserver.integration.icd10.utils.Icd10Mapper;
import com.kodality.termserver.integration.icd10.utils.Icd10ZipReader;
import jakarta.inject.Singleton;
import java.net.http.HttpResponse;
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
public class Icd10Service {
  private final TerminologyImportService importService;
  private final AssociationTypeService associationTypeService;
  private final BinaryHttpClient binaryHttpClient = new BinaryHttpClient();

  @Transactional
  public void importIcd10(String url, ImportConfiguration configuration) {
    prepareConfiguration(configuration);

    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(configuration);
    List<EntityProperty> properties = importService.prepareProperties(configuration, List.of("term", "synonym"));
    prepareAssociations();

    Icd10 diagnoses = new Icd10ZipReader().handleZipPack(getResource(url));
    List<Concept> concepts = Icd10Mapper.mapConcepts(diagnoses, configuration, properties);
    importService.importConcepts(concepts, version, configuration);
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return binaryHttpClient.GET(url).thenApply(HttpResponse::body).join();
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
    configuration.setUri(configuration.getUri() == null ? Icd10Configuration.uri : configuration.getUri());
    configuration.setVersion(configuration.getVersion() == null ? Icd10Configuration.version : configuration.getVersion());
    configuration.setSource(configuration.getSource() == null ? Icd10Configuration.source : configuration.getSource());
    configuration.setValidFrom(configuration.getValidFrom() == null ? LocalDate.now() : configuration.getValidFrom());
    configuration.setCodeSystem(configuration.getCodeSystem() == null ? Icd10Configuration.codeSystem : configuration.getCodeSystem());
    configuration.setCodeSystemName(configuration.getCodeSystemName() == null ? getCodeSystemName() : configuration.getCodeSystemName());
    configuration.setCodeSystemDescription(
        configuration.getCodeSystemDescription() == null ? Icd10Configuration.codeSystemDescription : configuration.getCodeSystemDescription());
  }

  private LocalizedName getCodeSystemName() {
    Map<String, String> ln = new HashMap<>();
    ln.put(Language.en, "ICD-10 WHO Edition");
    return new LocalizedName(ln);
  }
}
