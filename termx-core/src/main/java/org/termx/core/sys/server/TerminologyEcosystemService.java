package org.termx.core.sys.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
public class TerminologyEcosystemService {
  private final HttpClient httpClient;
  private final String coordinationServerUrl;

  public TerminologyEcosystemService(@Value("${terminology-ecosystem.coordination-server-url:http://tx.fhir.org/tx-reg}") String coordinationServerUrl) {
    this.coordinationServerUrl = coordinationServerUrl;
    this.httpClient = new HttpClient();
  }

  public JsonNode discovery(String registry, String server, String fhirVersion, String url, Boolean authoritativeOnly) {
    Map<String, String> params = new HashMap<>();
    if (StringUtils.isNotBlank(registry)) {
      params.put("registry", registry);
    }
    if (StringUtils.isNotBlank(server)) {
      params.put("server", server);
    }
    if (StringUtils.isNotBlank(fhirVersion)) {
      params.put("fhirVersion", fhirVersion);
    }
    if (StringUtils.isNotBlank(url)) {
      params.put("url", url);
    }
    if (authoritativeOnly != null) {
      params.put("authoritativeOnly", authoritativeOnly.toString());
    }

    String queryString = buildQueryString(params);
    String requestUrl = coordinationServerUrl + (queryString.isEmpty() ? "" : "?" + queryString);

    try {
      log.debug("Calling coordination server discovery: {}", requestUrl);
      HttpRequest request = httpClient.builder(requestUrl).GET().build();
      String response = httpClient.execute(request, BodyHandlers.ofString()).body();
      return JsonUtil.fromJson(response, JsonNode.class);
    } catch (Exception e) {
      log.error("Error calling coordination server at {}: {}", requestUrl, e.getMessage(), e);
      throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Failed to connect to terminology ecosystem coordination server");
    }
  }

  public JsonNode resolve(String fhirVersion, String url, String valueSet, Boolean authoritativeOnly, String usage) {
    if (StringUtils.isBlank(fhirVersion)) {
      throw new HttpStatusException(HttpStatus.BAD_REQUEST, "fhirVersion parameter is required");
    }
    if (StringUtils.isBlank(url) && StringUtils.isBlank(valueSet)) {
      throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Either 'url' or 'valueSet' parameter is required");
    }

    Map<String, String> params = new HashMap<>();
    params.put("fhirVersion", fhirVersion);
    if (StringUtils.isNotBlank(url)) {
      params.put("url", url);
    }
    if (StringUtils.isNotBlank(valueSet)) {
      params.put("valueSet", valueSet);
    }
    if (authoritativeOnly != null) {
      params.put("authoritativeOnly", authoritativeOnly.toString());
    }
    if (StringUtils.isNotBlank(usage)) {
      params.put("usage", usage);
    }

    String queryString = buildQueryString(params);
    String requestUrl = coordinationServerUrl + "/resolve?" + queryString;

    try {
      log.debug("Calling coordination server resolve: {}", requestUrl);
      HttpRequest request = httpClient.builder(requestUrl).GET().build();
      String response = httpClient.execute(request, BodyHandlers.ofString()).body();
      return JsonUtil.fromJson(response, JsonNode.class);
    } catch (Exception e) {
      log.error("Error calling coordination server at {}: {}", requestUrl, e.getMessage(), e);
      throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Failed to connect to terminology ecosystem coordination server");
    }
  }

  private String buildQueryString(Map<String, String> params) {
    if (params.isEmpty()) {
      return "";
    }
    return params.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .reduce((a, b) -> a + "&" + b)
        .orElse("");
  }
}
