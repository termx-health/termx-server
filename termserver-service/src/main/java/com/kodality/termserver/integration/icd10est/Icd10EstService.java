package com.kodality.termserver.integration.icd10est;

import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstMapper;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstZipReader;
import jakarta.inject.Singleton;
import java.util.List;
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
    List<Icd10Est> diagnoses = new Icd10EstZipReader().handleZipPack(getResource(url));
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning));
    importService.importCodeSystem(Icd10EstMapper.mapCodeSystem(configuration, diagnoses), associationTypes, false);
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return binaryHttpClient.GET(url).body();
  }
}
