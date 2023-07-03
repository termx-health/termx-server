package com.kodality.termx.orphanet;

import com.kodality.termx.http.BinaryHttpClient;
import com.kodality.termx.orphanet.utils.OrphanetClassificationList;
import com.kodality.termx.orphanet.utils.OrphanetClassificationList.ClassificationNode;
import com.kodality.termx.orphanet.utils.OrphanetMapper;
import com.kodality.termx.orphanet.utils.OrphanetXmlReader;
import com.kodality.termx.ts.CodeSystemImportProvider;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
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
  public void importOrpha(String url, CodeSystemImportConfiguration configuration) {
    OrphanetClassificationList classificationList = new OrphanetXmlReader().read(getResource(url));
    if (CollectionUtils.isEmpty(classificationList.getClassifications())) {
      return;
    }
    List<ClassificationNode> classificationNodes = classificationList.getClassifications().get(0).getClassificationNodeRootList().getClassificationNodes();
    importProvider.importCodeSystem(OrphanetMapper.toRequest(configuration, classificationNodes));
  }

  private byte[] getResource(String url) {
    log.info("Loading Orphanet content from {}", url);
    return client.GET(url).body();
  }
}
