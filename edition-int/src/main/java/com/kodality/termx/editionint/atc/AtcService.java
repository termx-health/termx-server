package com.kodality.termx.editionint.atc;


import com.kodality.termx.editionint.atc.utils.AtcMapper;
import com.kodality.termx.editionint.atc.utils.AtcResponseParser;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termx.core.ts.CodeSystemImportProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class AtcService {

  private final CodeSystemImportProvider importProvider;

  @Transactional
  public void importAtc(CodeSystemImportConfiguration configuration) {
    Map<String, String> atc = AtcResponseParser.parse(getResource());
    importProvider.importCodeSystem(AtcMapper.toRequest(configuration, atc));
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
