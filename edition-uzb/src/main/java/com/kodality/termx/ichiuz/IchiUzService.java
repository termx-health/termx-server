package com.kodality.termx.ichiuz;


import com.kodality.termx.http.BinaryHttpClient;
import com.kodality.termx.ichiuz.utils.IchiUz;
import com.kodality.termx.ichiuz.utils.IchiUzCsvReader;
import com.kodality.termx.ichiuz.utils.IchiUzMapper;
import com.kodality.termx.ts.CodeSystemImportProvider;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class IchiUzService {
  private final CodeSystemImportProvider importProvider;
  private final BinaryHttpClient binaryHttpClient = new BinaryHttpClient();

  @Transactional
  public void importIchiUz(String url, CodeSystemImportConfiguration configuration) {
    List<IchiUz> activities = IchiUzCsvReader.read(getResource(url));
    importProvider.importCodeSystem(IchiUzMapper.toRequest(configuration, activities));
  }

  private byte[] getResource(String url) {
    log.info("Loading ICHI uz CSV from {}", url);
    return binaryHttpClient.GET(url).body();
  }
}
