package org.termx.terminology.terminology;

import com.fasterxml.jackson.databind.JsonNode;
import com.kodality.commons.util.JsonUtil;
import org.termx.core.util.canonical.AuthoritativeUrlMatcher;
import org.termx.core.util.canonical.CanonicalUrlParser;
import org.termx.sys.server.TerminologyServer;
import org.termx.sys.server.TerminologyServer.AuthoritativeResource;
import org.termx.sys.server.resource.TerminologyServerResourceListProvider;
import org.termx.terminology.terminology.FhirServerHttpClientService.FhirServerHttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Lists resources from a remote FHIR terminology server by paging its type collection endpoint
 * (e.g. {@code GET <root>/CodeSystem?_elements=url,version,status}) and keeping only the resources whose
 * canonical URL matches the server's authoritative patterns. Fetching goes through the same auth-aware
 * {@link FhirServerHttpClient} as real calls. Best-effort: transport/parse failures yield whatever was
 * collected so far rather than an error.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class FhirServerResourceListProvider implements TerminologyServerResourceListProvider {
  private final FhirServerHttpClientService fhirClientService;

  /** Maps the UI resource-type slug to the FHIR resource type name. */
  private static final Map<String, String> FHIR_TYPES = Map.of(
      "code-systems", "CodeSystem",
      "value-sets", "ValueSet",
      "concept-maps", "ConceptMap",
      "structure-definitions", "StructureDefinition",
      "structure-maps", "StructureMap");

  private static final int MAX_PAGES = 50;

  @Override
  public boolean checkType(String type) {
    return FHIR_TYPES.containsKey(type);
  }

  @Override
  public List<AuthoritativeResource> list(TerminologyServer server, String type, List<AuthoritativeResource> patterns) {
    String fhirType = FHIR_TYPES.get(type);
    List<AuthoritativeResource> result = new ArrayList<>();
    if (fhirType == null || server == null || server.getId() == null) {
      return result;
    }

    FhirServerHttpClient client = fhirClientService.getHttpClient(server.getId());
    String baseUrl = StringUtils.stripEnd(server.getRootUrl(), "/");
    String path = fhirType + "?_elements=url,version,status&_count=200";

    try {
      for (int page = 0; page < MAX_PAGES && path != null; page++) {
        HttpRequest request = client.builder(path).header("Accept", "application/fhir+json").GET().build();
        HttpResponse<String> response = client.executeAsync(request).join();
        JsonNode bundle = JsonUtil.getObjectMapper().readTree(response.body());
        collectMatches(bundle.get("entry"), fhirType, patterns, result);
        path = nextPagePath(bundle, baseUrl);
      }
    } catch (Exception e) {
      log.warn("Failed to list {} from remote server {}: {}", type, server.getCode(), e.getMessage());
    }
    return result;
  }

  private void collectMatches(JsonNode entries, String fhirType, List<AuthoritativeResource> patterns, List<AuthoritativeResource> result) {
    if (entries == null || !entries.isArray()) {
      return;
    }
    for (JsonNode entry : entries) {
      JsonNode resource = entry.get("resource");
      if (resource == null) {
        continue;
      }
      String uri = text(resource, "url");
      String version = text(resource, "version");
      String status = text(resource, "status");
      String id = text(resource, "id");
      for (AuthoritativeResource pattern : patterns) {
        if (matches(pattern, uri, version, status, fhirType)) {
          result.add(new AuthoritativeResource().setUrl(uri).setVersion(version).setStatus(status).setName(id));
          break;
        }
      }
    }
  }

  /** Follows the {@code next} bundle link, but only when it points back at the same server. */
  private String nextPagePath(JsonNode bundle, String baseUrl) {
    JsonNode links = bundle.get("link");
    if (links == null || !links.isArray()) {
      return null;
    }
    for (JsonNode link : links) {
      if ("next".equals(text(link, "relation"))) {
        String url = text(link, "url");
        if (url != null && url.startsWith(baseUrl)) {
          return url.substring(baseUrl.length()).replaceFirst("^/", "");
        }
        return null;
      }
    }
    return null;
  }

  private boolean matches(AuthoritativeResource pattern, String uri, String version, String status, String fhirType) {
    CanonicalUrlParser parsed = CanonicalUrlParser.parse(pattern.toEcosystemUrl());
    if (!AuthoritativeUrlMatcher.matches(parsed.getBaseUrl(), uri, fhirType)) {
      return false;
    }
    if (parsed.getVersion() != null && version != null && !parsed.getVersion().equals(version)) {
      return false;
    }
    return parsed.getStatus() == null || status == null || parsed.getStatus().equals(status);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value != null && !value.isNull() ? value.asText() : null;
  }
}
