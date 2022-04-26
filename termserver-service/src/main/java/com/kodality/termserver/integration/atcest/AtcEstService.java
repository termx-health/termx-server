package com.kodality.termserver.integration.atcest;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.Language;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.association.AssociationTypeService;
import com.kodality.termserver.integration.atcest.utils.AtcEst;
import com.kodality.termserver.integration.atcest.utils.AtcEstCsvReader;
import com.kodality.termserver.integration.atcest.utils.AtcEstMapper;
import com.kodality.termserver.integration.common.BinaryHttpClient;
import com.kodality.termserver.integration.common.ImportConfiguration;
import com.kodality.termserver.integration.common.TerminologyImportService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class AtcEstService {

  private final TerminologyImportService importService;
  private final AssociationTypeService associationTypeService;

  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importAtcEst(String url, ImportConfiguration configuration) {
    prepareConfiguration(configuration);

    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(configuration);
    List<EntityProperty> properties = importService.prepareProperties(configuration, List.of("term"));
    prepareAssociations();

    List<AtcEst> atc = AtcEstCsvReader.read(getResource(url));
    List<Concept> concepts = AtcEstMapper.mapConcepts(atc, configuration, properties);
    concepts.add(0, AtcEstMapper.createRootConcept(configuration, properties));
    importService.importConcepts(concepts, version, configuration);
  }

  private byte[] getResource(String url) {
    log.info("Loading ATC CSV from {}", url);
    return client.GET(url).body();
  }

  private void prepareAssociations() {
    AssociationType associationType = new AssociationType();
    associationType.setCode("is-a");
    associationType.setForwardName("Is a");
    associationType.setDirected(true);
    associationType.setAssociationKind("code-system-hierarchy");
    associationTypeService.save(associationType);
  }

  private void prepareConfiguration(ImportConfiguration configuration) {
    configuration.setUri(configuration.getUri() == null ? AtcEstConfiguration.uri : configuration.getUri());
    configuration.setVersion(configuration.getVersion() == null ? String.valueOf(LocalDate.now().getYear()) : configuration.getVersion());
    configuration.setSource(configuration.getSource() == null ? AtcEstConfiguration.source : configuration.getSource());
    configuration.setValidFrom(configuration.getValidFrom() == null ? LocalDate.now() : configuration.getValidFrom());
    configuration.setCodeSystem(configuration.getCodeSystem() == null ? AtcEstConfiguration.codeSystem : configuration.getCodeSystem());
    configuration.setCodeSystemName(configuration.getCodeSystemName() == null ? getCodeSystemName() : configuration.getCodeSystemName());
    configuration.setCodeSystemDescription(
        configuration.getCodeSystemDescription() == null ? AtcEstConfiguration.codeSystemDescription : configuration.getCodeSystemDescription());
  }

  private LocalizedName getCodeSystemName() {
    Map<String, String> ln = new HashMap<>();
    ln.put(Language.et, "Eesti ATC");
    ln.put(Language.en, "Estonian ATC");
    return new LocalizedName(ln);
  }
}
