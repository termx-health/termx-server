package com.kodality.termserver.integration.icd10;

import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.icd10.utils.Icd10;
import com.kodality.termserver.integration.icd10.utils.Icd10Mapper;
import com.kodality.termserver.integration.icd10.utils.Icd10ZipReader;
import jakarta.inject.Singleton;
import java.util.List;
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
    Icd10 diagnoses = new Icd10ZipReader().handleZipPack(getResource(url));
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning));
    importService.importCodeSystem(Icd10Mapper.mapCodeSystem(configuration, diagnoses), associationTypes, false);
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return client.GET(url).body();
  }
}
