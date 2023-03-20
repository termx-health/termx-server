package com.kodality.termserver.atcest;

import com.kodality.termserver.BinaryHttpClient;
import com.kodality.termserver.atcest.utils.AtcEst;
import com.kodality.termserver.atcest.utils.AtcEstCsvReader;
import com.kodality.termserver.atcest.utils.AtcEstMapper;
import com.kodality.termserver.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termserver.ts.CodeSystemImportProvider;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
public class AtcEstService {
  @Inject
  private CodeSystemImportProvider importProvider;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importAtcEst(String url, CodeSystemImportConfiguration request) {
    List<AtcEst> atc = AtcEstCsvReader.read(getResource(url));
    importProvider.importCodeSystem(AtcEstMapper.toRequest(request, atc));
  }

  private byte[] getResource(String url) {
    log.info("Loading ATC CSV from {}", url);
    return client.GET(url).body();
  }
}


