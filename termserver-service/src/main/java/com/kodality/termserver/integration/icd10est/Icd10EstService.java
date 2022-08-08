package com.kodality.termserver.integration.icd10est;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.Language;
import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstExtractor;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstMapper;
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
  private final CodeSystemImportService importService;
  private final BinaryHttpClient binaryHttpClient = new BinaryHttpClient();

  @Transactional
  public void importIcd10Est(String url, ImportConfiguration configuration) {
    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(Icd10EstMapper.mapCodeSystem(configuration));
    List<EntityProperty> properties = importService.prepareProperties(Icd10EstMapper.mapProperties(), configuration.getCodeSystem());
    importService.prepareAssociationType("is-a", AssociationKind.codesystemHierarchyMeaning);

    List<Icd10Est> diagnoses = new Icd10EstZipReader().handleZipPack(getResource(url));
    List<Concept> concepts = Icd10EstExtractor.parseDiagnoses(diagnoses, configuration, properties);
    importService.importConcepts(concepts, version, false);
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return binaryHttpClient.GET(url).body();
  }
}
