package com.kodality.termserver.integration.icd10;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.Language;
import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.integration.icd10.utils.Icd10;
import com.kodality.termserver.integration.icd10.utils.Icd10Mapper;
import com.kodality.termserver.integration.icd10.utils.Icd10ZipReader;
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
public class Icd10Service {
  private final CodeSystemImportService importService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importIcd10(String url, ImportConfiguration configuration) {
    prepareConfiguration(configuration);

    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(Icd10Mapper.mapCodeSystem(configuration));
    List<EntityProperty> properties = importService.prepareProperties(Icd10Mapper.mapProperties(), configuration.getCodeSystem());
    importService.prepareAssociationType("is-a", AssociationKind.codesystemHierarchyMeaning);

    Icd10 diagnoses = new Icd10ZipReader().handleZipPack(getResource(url));
    List<Concept> concepts = Icd10Mapper.mapConcepts(diagnoses, configuration, properties);
    importService.importConcepts(concepts, version, false);
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return client.GET(url).body();
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
