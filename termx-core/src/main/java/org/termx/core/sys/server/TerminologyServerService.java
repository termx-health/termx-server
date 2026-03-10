package org.termx.core.sys.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import org.termx.core.ApiError;
import org.termx.core.sys.server.httpclient.ServerHttpClientProvider;
import org.termx.sys.server.TerminologyServer;
import org.termx.sys.server.TerminologyServer.AuthoritativeResource;
import org.termx.sys.server.TerminologyServer.TerminologyServerFhirVersion;
import org.termx.sys.server.TerminologyServerQueryParams;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerService {
  private final TerminologyServerRepository repository;
  private final List<ServerHttpClientProvider> httpClientServices;

  public List<String> getKinds() {
    return httpClientServices.stream().map(ServerHttpClientProvider::getKind).distinct().toList();
  }

  @Transactional
  public TerminologyServer save(TerminologyServer server) {
    prepare(server);
    validate(server);

    repository.save(server);
    httpClientServices.forEach(hc -> hc.afterServerSave(server.getId()));
    return server;
  }

  public TerminologyServer load(Long id) {
    return repository.load(id);
  }

  public TerminologyServer load(String code) {
    return repository.load(code);
  }

  public TerminologyServer loadCurrentInstallation() {
    return repository.loadCurrentInstallation();
  }

  public QueryResult<TerminologyServer> query(TerminologyServerQueryParams params) {
    return repository.query(params);
  }

  private void prepare(TerminologyServer server) {
    TerminologyServer persisted = load(server.getId());

    if (persisted != null) {
      if (persisted.getAuthConfig() != null && server.getAuthConfig() != null && server.getAuthConfig().getClientSecret() == null) {
        server.getAuthConfig().setClientSecret(persisted.getAuthConfig().getClientSecret());
      }
      if (persisted.getHeaders() != null && server.getHeaders() != null) {
        var unmodifiedAuthHeader = server.getHeaders().stream().filter(h -> h.getKey().equals("Authorization") && h.getValue() == null).findFirst();
        var persistedAuthHeader = persisted.getHeaders().stream().filter(h -> h.getKey().equals("Authorization")).findFirst();

        if (unmodifiedAuthHeader.isPresent() && persistedAuthHeader.isPresent()) {
          unmodifiedAuthHeader.get().setValue(persistedAuthHeader.get().getValue());
        }
      }
    }
  }

  private void validate(TerminologyServer server) {
    if (server.isCurrentInstallation()) {
      TerminologyServer currentInstallation = loadCurrentInstallation();
      if (currentInstallation != null && !currentInstallation.getId().equals(server.getId())) {
        throw ApiError.TC101.toApiException();
      }
    }
    if (server.getHeaders() != null) {
      if (server.getHeaders().stream().filter(h -> h.getKey().equals("Authorization")).count() > 1) {
        throw ApiError.TC109.toApiException(Map.of("name", "Authorization"));
      }
    }
  }

  // -- Export --

  public String exportToEcosystemFormat() {
    List<TerminologyServer> servers = repository.query(new TerminologyServerQueryParams()).getData();
    return buildEcosystemJson(servers);
  }

  private String buildEcosystemJson(List<TerminologyServer> servers) {
    Map<String, Object> registry = new LinkedHashMap<>();
    registry.put("formatVersion", "1");
    registry.put("description", "TermX Terminology Servers");

    List<Map<String, Object>> serverList = servers.stream()
        .filter(TerminologyServer::isActive)
        .map(this::convertToEcosystemServer)
        .collect(Collectors.toList());

    registry.put("servers", serverList);
    return JsonUtil.toJson(registry);
  }

  private Map<String, Object> convertToEcosystemServer(TerminologyServer ts) {
    Map<String, Object> server = new LinkedHashMap<>();
    server.put("code", ts.getCode());
    server.put("name", extractName(ts.getNames()));

    if (StringUtils.isNotBlank(ts.getAccessInfo())) {
      server.put("access_info", ts.getAccessInfo());
    }
    if (ts.getRootUrl() != null) {
      server.put("url", ts.getRootUrl());
    }
    if (ts.getUsage() != null && !ts.getUsage().isEmpty()) {
      server.put("usage", ts.getUsage());
    }

    if (ts.getAuthConfig() != null) {
      server.put("oauth", true);
    } else if (hasAuthHeaders(ts)) {
      server.put("token", true);
    } else {
      server.put("open", true);
    }

    server.put("authoritative", toCanonicalList(ts.getAuthoritative()));
    server.put("authoritative-valuesets", toCanonicalList(ts.getAuthoritativeValuesets()));
    server.put("exclusions", ts.getExclusions() != null ? ts.getExclusions() : new ArrayList<>());

    if (ts.getFhirVersions() != null && !ts.getFhirVersions().isEmpty()) {
      List<Map<String, String>> fv = ts.getFhirVersions().stream().map(v -> {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("version", v.getVersion());
        m.put("url", v.getUrl());
        return m;
      }).collect(Collectors.toList());
      server.put("fhirVersions", fv);
    } else {
      Map<String, String> defaultVersion = new LinkedHashMap<>();
      defaultVersion.put("version", "R5");
      defaultVersion.put("url", ts.getRootUrl());
      server.put("fhirVersions", List.of(defaultVersion));
    }

    if (ts.getSupportedOperations() != null && !ts.getSupportedOperations().isEmpty()) {
      server.put("supportedOperations", ts.getSupportedOperations());
    }

    return server;
  }

  private List<String> toCanonicalList(List<AuthoritativeResource> resources) {
    if (resources == null || resources.isEmpty()) {
      return new ArrayList<>();
    }
    return resources.stream().map(r -> {
      if (StringUtils.isNotBlank(r.getVersion())) {
        return r.getUrl() + "|" + r.getVersion();
      }
      return r.getUrl();
    }).collect(Collectors.toList());
  }

  private String extractName(LocalizedName names) {
    if (names == null) {
      return null;
    }
    String name = names.get("en");
    if (name == null && !names.isEmpty()) {
      name = names.values().iterator().next();
    }
    return name;
  }

  private boolean hasAuthHeaders(TerminologyServer ts) {
    return ts.getHeaders() != null &&
        ts.getHeaders().stream().anyMatch(h -> "Authorization".equals(h.getKey()));
  }

  // -- Import --

  @Transactional
  public List<TerminologyServer> importFromEcosystemFormat(String json) {
    JsonNode root = JsonUtil.fromJson(json, JsonNode.class);
    JsonNode serversNode = root.get("servers");
    if (serversNode == null || !serversNode.isArray()) {
      return new ArrayList<>();
    }

    List<TerminologyServer> result = new ArrayList<>();
    for (JsonNode serverNode : serversNode) {
      TerminologyServer ts = parseEcosystemServer(serverNode);
      TerminologyServer existing = repository.load(ts.getCode());
      if (existing != null) {
        ts.setId(existing.getId());
        ts.setHeaders(existing.getHeaders());
        ts.setAuthConfig(existing.getAuthConfig());
        ts.setCurrentInstallation(existing.isCurrentInstallation());
      }
      repository.save(ts);
      result.add(ts);
    }
    return result;
  }

  private TerminologyServer parseEcosystemServer(JsonNode node) {
    TerminologyServer ts = new TerminologyServer();
    ts.setCode(textValue(node, "code"));

    LocalizedName names = new LocalizedName();
    names.put("en", textValue(node, "name"));
    ts.setNames(names);

    ts.setAccessInfo(textValue(node, "access_info"));
    ts.setActive(true);
    ts.setKind(List.of("terminology"));

    // Parse fhirVersions and use first URL as rootUrl
    JsonNode fvNode = node.get("fhirVersions");
    if (fvNode != null && fvNode.isArray() && !fvNode.isEmpty()) {
      List<TerminologyServerFhirVersion> fhirVersions = new ArrayList<>();
      for (JsonNode v : fvNode) {
        TerminologyServerFhirVersion fv = new TerminologyServerFhirVersion();
        fv.setVersion(textValue(v, "version"));
        fv.setUrl(textValue(v, "url"));
        fhirVersions.add(fv);
      }
      ts.setFhirVersions(fhirVersions);
      ts.setRootUrl(fhirVersions.get(0).getUrl());
    } else {
      String url = textValue(node, "url");
      ts.setRootUrl(url != null ? url : "");
    }

    ts.setUsage(stringList(node, "usage"));
    ts.setExclusions(stringList(node, "exclusions"));
    ts.setAuthoritative(parseAuthoritativeList(node.get("authoritative")));
    ts.setAuthoritativeValuesets(parseAuthoritativeList(node.get("authoritative-valuesets")));

    JsonNode opsNode = node.get("supportedOperations");
    if (opsNode != null && opsNode.isArray()) {
      ts.setSupportedOperations(stringList(node, "supportedOperations"));
    }

    return ts;
  }

  private List<AuthoritativeResource> parseAuthoritativeList(JsonNode node) {
    if (node == null || !node.isArray()) {
      return new ArrayList<>();
    }
    List<AuthoritativeResource> result = new ArrayList<>();
    for (JsonNode item : node) {
      AuthoritativeResource ar = new AuthoritativeResource();
      String canonical = item.asText();
      if (canonical.contains("|")) {
        String[] parts = canonical.split("\\|", 2);
        ar.setUrl(parts[0]);
        ar.setVersion(parts[1]);
      } else {
        ar.setUrl(canonical);
      }
      result.add(ar);
    }
    return result;
  }

  private String textValue(JsonNode node, String field) {
    JsonNode child = node.get(field);
    return child != null && !child.isNull() ? child.asText() : null;
  }

  private List<String> stringList(JsonNode node, String field) {
    JsonNode child = node.get(field);
    if (child == null || !child.isArray()) {
      return null;
    }
    List<String> list = new ArrayList<>();
    for (JsonNode item : child) {
      list.add(item.asText());
    }
    return list.isEmpty() ? null : list;
  }
}
