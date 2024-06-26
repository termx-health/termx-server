package com.kodality.termx.editionint.icd10;

import com.kodality.termx.core.http.BinaryHttpClient;
import com.kodality.termx.editionint.icd10.utils.Icd10;
import com.kodality.termx.editionint.icd10.utils.Icd10Mapper;
import com.kodality.termx.editionint.icd10.utils.Icd10ZipReader;
import com.kodality.termx.core.ts.CodeSystemImportProvider;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class Icd10Service {
  private final CodeSystemImportProvider importProvider;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importIcd10(String url, CodeSystemImportConfiguration configuration) {
    Icd10 diagnoses = new Icd10ZipReader().handleZipPack(getResource(url));
    importProvider.importCodeSystem(Icd10Mapper.toRequest(configuration, diagnoses));
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return client.GET(url).body();
  }
}
