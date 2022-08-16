package com.kodality.termserver.integration.atc;


import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
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
    Map<String, String> atc = AtcResponseParser.parse(getResource());
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    importService.importCodeSystem(AtcMapper.mapCodeSystem(configuration, atc), associationTypes, false);
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
