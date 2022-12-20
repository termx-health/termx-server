package com.kodality.termserver.integration.ichiuz;


import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.atcest.utils.AtcEst;
import com.kodality.termserver.integration.atcest.utils.AtcEstCsvReader;
import com.kodality.termserver.integration.atcest.utils.AtcEstMapper;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstMapper;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstZipReader;
import com.kodality.termserver.integration.ichiuz.utils.IchiUz;
import com.kodality.termserver.integration.ichiuz.utils.IchiUzCsvReader;
import com.kodality.termserver.integration.ichiuz.utils.IchiUzMapper;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class IchiUzService {
  private final CodeSystemImportService importService;
  private final BinaryHttpClient binaryHttpClient = new BinaryHttpClient();

  @Transactional
  public void importIchiUz(String url, ImportConfiguration configuration) {
    List<IchiUz> activities = IchiUzCsvReader.read(getResource(url));
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    importService.importCodeSystem(IchiUzMapper.mapCodeSystem(configuration, activities), associationTypes, false);
  }

  private byte[] getResource(String url) {
    log.info("Loading ICHI uz CSV from {}", url);
    return binaryHttpClient.GET(url).body();
  }
}
