package org.termx.editionint.orphanet;

import org.termx.core.http.BinaryHttpClient;
import org.termx.editionint.orphanet.utils.ClassificationList;
import org.termx.editionint.orphanet.utils.DisorderList;
import org.termx.editionint.orphanet.utils.OrphanetMapper;
import org.termx.editionint.orphanet.utils.OrphanetXmlReader;
import org.termx.core.ts.CodeSystemImportProvider;
import org.termx.ts.codesystem.CodeSystemImportConfiguration;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class OrphanetService {
  private final CodeSystemImportProvider importProvider;

  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importOrpha(byte[] file, CodeSystemImportConfiguration configuration) {
    ClassificationList classificationList = new OrphanetXmlReader().read(file, ClassificationList.class);
    DisorderList disorderList = new OrphanetXmlReader().read(file, DisorderList.class);
    if (classificationList != null && CollectionUtils.isNotEmpty(classificationList.getClassifications())) {
      importProvider.importCodeSystem(OrphanetMapper.toRequest(configuration, classificationList));
    }
    if (disorderList != null && CollectionUtils.isNotEmpty(disorderList.getDisorders())) {
      importProvider.importCodeSystem(OrphanetMapper.toRequest(configuration, disorderList));
    }
  }

  @Transactional
  public void importOrpha(CodeSystemImportConfiguration configuration) {
    importOrpha(getResource(configuration.getSourceUrl()), configuration);
  }

  private byte[] getResource(String url) {
    log.info("Loading Orphanet content from {}", url);
    return client.GET(url).body();
  }
}
