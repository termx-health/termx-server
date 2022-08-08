package com.kodality.termserver.integration.atc;


import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.atc.utils.AtcMapper;
import com.kodality.termserver.integration.atc.utils.AtcResponseParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class AtcService {

  private final CodeSystemImportService importService;

  @Transactional
  public void importAtc(ImportConfiguration configuration) {
    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(AtcMapper.mapCodeSystem(configuration));
    List<EntityProperty> properties = importService.prepareProperties(AtcMapper.mapProperties(), configuration.getCodeSystem());
    importService.prepareAssociationType("is-a", AssociationKind.codesystemHierarchyMeaning);

    Map<String, String> atc = AtcResponseParser.parse(getResource());
    List<Concept> concepts = AtcMapper.mapConcepts(atc, configuration, properties);

    importService.importConcepts(concepts, version, false);
  }

  private String getResource() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    HttpRequest request = prepareRequest();
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private HttpRequest prepareRequest() {
    return HttpRequest.newBuilder()
        .uri(URI.create("https://www.whocc.no/atc_ddd_index/"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString("code=ATC+code&name=%%%&namesearchtype=containing"))
        .build();
  }
}
