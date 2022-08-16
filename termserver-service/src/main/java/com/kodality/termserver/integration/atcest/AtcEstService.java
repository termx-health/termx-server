package com.kodality.termserver.integration.atcest;

import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.atcest.utils.AtcEst;
import com.kodality.termserver.integration.atcest.utils.AtcEstCsvReader;
import com.kodality.termserver.integration.atcest.utils.AtcEstMapper;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class AtcEstService {

  private final CodeSystemImportService importService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importAtcEst(String url, ImportConfiguration configuration) {
    List<AtcEst> atc = AtcEstCsvReader.read(getResource(url));
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    importService.importCodeSystem(AtcEstMapper.mapCodeSystem(configuration, atc), associationTypes, false);
  }

  private byte[] getResource(String url) {
    log.info("Loading ATC CSV from {}", url);
    return client.GET(url).body();
  }
}
