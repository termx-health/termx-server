package com.kodality.termserver.integration.atcest;

import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
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
    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(AtcEstMapper.mapCodeSystem(configuration));
    List<EntityProperty> properties = importService.prepareProperties(AtcEstMapper.mapProperties(), configuration.getCodeSystem());
    importService.prepareAssociationType("is-a", AssociationKind.codesystemHierarchyMeaning);

    List<AtcEst> atc = AtcEstCsvReader.read(getResource(url));
    List<Concept> concepts = AtcEstMapper.mapConcepts(atc, configuration, properties);
    importService.importConcepts(concepts, version, false);
  }

  private byte[] getResource(String url) {
    log.info("Loading ATC CSV from {}", url);
    return client.GET(url).body();
  }
}
