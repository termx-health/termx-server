package org.termx.core.sys.ecosystem;

import com.kodality.commons.model.QueryResult;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.exceptions.HttpStatusException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.termx.core.sys.server.TerminologyServerService;
import org.termx.sys.ecosystem.Ecosystem;
import org.termx.sys.ecosystem.EcosystemQueryParams;
import org.termx.sys.server.TerminologyServer;

/**
 * Public (unsecured) API for ecosystem definitions.
 * Path prefix /public is in AuthorizationFilter.DEFAULT_PUBLIC, bypassing auth.
 */
@Controller("/public/ecosystems")
@RequiredArgsConstructor
public class EcosystemPublicController {
  private final EcosystemService ecosystemService;
  private final TerminologyServerService terminologyServerService;

  @Get
  public List<EcosystemPublicListItem> list() {
    EcosystemQueryParams params = new EcosystemQueryParams();
    params.setLimit(-1);
    QueryResult<Ecosystem> result = ecosystemService.query(params);
    return result.getData().stream()
        .filter(Ecosystem::isActive)
        .map(e -> new EcosystemPublicListItem(e.getCode(), e.getNames()))
        .toList();
  }

  @Get("{ecosystemCode}")
  public Map<String, Object> load(@PathVariable String ecosystemCode) {
    Ecosystem ecosystem = ecosystemService.load(ecosystemCode);
    if (ecosystem == null) {
      throw new HttpStatusException(HttpStatus.NOT_FOUND, "Ecosystem not found");
    }
    return toEcosystemJson(ecosystem);
  }

  @Get("{ecosystemCode}/server/{serverCode}")
  public Map<String, Object> loadServer(@PathVariable String ecosystemCode, @PathVariable String serverCode) {
    Ecosystem ecosystem = ecosystemService.load(ecosystemCode);
    if (ecosystem == null) {
      throw new HttpStatusException(HttpStatus.NOT_FOUND, "Ecosystem not found");
    }
    for (Long serverId : ecosystem.getServerIds()) {
      TerminologyServer ts = terminologyServerService.load(serverId);
      if (ts != null && serverCode.equals(ts.getCode())) {
        return terminologyServerService.convertToEcosystemServer(ts);
      }
    }
    throw new HttpStatusException(HttpStatus.NOT_FOUND, "Server not found in ecosystem");
  }

  /**
   * Returns ecosystem.json content directly: {formatVersion, description, servers: [...]}
   */
  private Map<String, Object> toEcosystemJson(Ecosystem ecosystem) {
    List<Map<String, Object>> servers = ecosystem.getServerIds().stream()
        .map(terminologyServerService::load)
        .filter(ts -> ts != null && ts.isActive())
        .map(terminologyServerService::convertToEcosystemServer)
        .toList();

    Map<String, Object> json = new LinkedHashMap<>();
    json.put("formatVersion", ecosystem.getFormatVersion());
    if (ecosystem.getDescription() != null) {
      json.put("description", ecosystem.getDescription());
    }
    json.put("servers", servers);
    return json;
  }

  public record EcosystemPublicListItem(String code, Object names) {}
}
