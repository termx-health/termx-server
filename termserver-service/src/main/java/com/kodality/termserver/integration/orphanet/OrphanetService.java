package com.kodality.termserver.integration.orphanet;

import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.orphanet.utils.OrphanetClassificationList;
import com.kodality.termserver.integration.orphanet.utils.OrphanetClassificationList.ClassificationNode;
import com.kodality.termserver.integration.orphanet.utils.OrphanetMapper;
import com.kodality.termserver.integration.orphanet.utils.OrphanetXmlReader;
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
  private final CodeSystemImportService importService;

  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importOrpha(String url, ImportConfiguration configuration) {
    OrphanetClassificationList classificationList = new OrphanetXmlReader().read(getResource(url));
    if (CollectionUtils.isEmpty(classificationList.getClassifications())) {
      return;
    }
    List<ClassificationNode> classificationNodes = classificationList.getClassifications().get(0).getClassificationNodeRootList().getClassificationNodes();
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    importService.importCodeSystem(OrphanetMapper.mapCodeSystem(configuration, classificationNodes), associationTypes, false);
  }

  private byte[] getResource(String url) {
    log.info("Loading Orphanet content from {}", url);
    return client.GET(url).body();
  }
}
