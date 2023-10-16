package com.kodality.termx.editionest.icd10est;

import com.kodality.termx.core.http.BinaryHttpClient;
import com.kodality.termx.editionest.icd10est.utils.Icd10Est;
import com.kodality.termx.editionest.icd10est.utils.Icd10EstMapper;
import com.kodality.termx.editionest.icd10est.utils.Icd10EstZipReader;
import com.kodality.termx.core.ts.CodeSystemImportProvider;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class Icd10EstService {
  private final CodeSystemImportProvider importProvider;
  private final BinaryHttpClient binaryHttpClient = new BinaryHttpClient();

  @Transactional
  public void importIcd10Est(String url, CodeSystemImportConfiguration configuration) {
    List<Icd10Est> diagnoses = new Icd10EstZipReader().handleZipPack(getResource(url));
    importProvider.importCodeSystem(Icd10EstMapper.toRequest(configuration, diagnoses));
  }

  private byte[] getResource(String url) {
    log.info("Loading ICD-10 ZIP from {}", url);
    return binaryHttpClient.GET(url).body();
  }
}
