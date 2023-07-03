package com.kodality.termx.atcest;

import com.kodality.termx.atcest.utils.AtcEst;
import com.kodality.termx.atcest.utils.AtcEstCsvReader;
import com.kodality.termx.atcest.utils.AtcEstMapper;
import com.kodality.termx.http.BinaryHttpClient;
import com.kodality.termx.ts.CodeSystemImportProvider;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
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


