package org.termx.core.sys.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import lombok.RequiredArgsConstructor;
import java.util.Optional;

@Controller("/tx-reg")
@RequiredArgsConstructor
public class TerminologyEcosystemController {
  private final TerminologyEcosystemService ecosystemService;

  @Get(produces = MediaType.APPLICATION_JSON)
  public HttpResponse<JsonNode> discovery(
      @QueryValue Optional<String> registry,
      @QueryValue Optional<String> server,
      @QueryValue Optional<String> fhirVersion,
      @QueryValue Optional<String> url,
      @QueryValue Optional<Boolean> authoritativeOnly,
      @QueryValue Optional<Boolean> download) {

    JsonNode result = ecosystemService.discovery(
        registry.orElse(null),
        server.orElse(null),
        fhirVersion.orElse(null),
        url.orElse(null),
        authoritativeOnly.orElse(null)
    );

    MutableHttpResponse<JsonNode> response = HttpResponse.ok(result)
        .contentType(MediaType.APPLICATION_JSON);

    if (download.orElse(false)) {
      response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tx-servers.json\"")
          .header(HttpHeaders.CACHE_CONTROL, "no-cache");
    }

    return response;
  }

  @Get(uri = "/resolve", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<JsonNode> resolve(
      @QueryValue String fhirVersion,
      @QueryValue Optional<String> url,
      @QueryValue Optional<String> valueSet,
      @QueryValue Optional<Boolean> authoritativeOnly,
      @QueryValue Optional<String> usage,
      @QueryValue Optional<Boolean> download) {

    JsonNode result = ecosystemService.resolve(
        fhirVersion,
        url.orElse(null),
        valueSet.orElse(null),
        authoritativeOnly.orElse(null),
        usage.orElse(null)
    );

    MutableHttpResponse<JsonNode> response = HttpResponse.ok(result)
        .contentType(MediaType.APPLICATION_JSON);

    if (download.orElse(false)) {
      response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tx-resolve.json\"")
          .header(HttpHeaders.CACHE_CONTROL, "no-cache");
    }

    return response;
  }
}
