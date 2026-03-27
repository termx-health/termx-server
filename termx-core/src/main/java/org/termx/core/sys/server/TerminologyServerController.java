package org.termx.core.sys.server;

import com.kodality.commons.model.QueryResult;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.sys.server.TerminologyServer;
import org.termx.sys.server.TerminologyServerQueryParams;
import org.termx.sys.server.resource.TerminologyServerResourceRequest;
import org.termx.sys.server.resource.TerminologyServerResourceResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import org.termx.sys.server.TerminologyServer.AuthoritativeResource;
import java.util.List;
import java.util.Optional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/servers")
@RequiredArgsConstructor
public class TerminologyServerController {
  private final TerminologyServerService serverService;
  private final TerminologyServerResourceService serverResourceService;
  private final TerminologyServerAuthoritativeService authoritativeService;

  @Authorized("Server.view")
  @Get("/kinds")
  public List<String> getKinds() {
    return serverService.getKinds();
  }

  @Authorized("Server.edit")
  @Post
  public TerminologyServer create(@Valid @Body TerminologyServer ts) {
    ts.setId(null);
    return serverService.save(ts).maskSensitiveData();
  }

  @Authorized("Server.edit")
  @Put("/{id}")
  public TerminologyServer update(@PathVariable Long id, @Valid @Body TerminologyServer ts) {
    ts.setId(id);
    return serverService.save(ts).maskSensitiveData();
  }

  @Authorized("Server.edit")
  @Get("/{id}")
  public TerminologyServer load(@PathVariable Long id) {
    return serverService.load(id).maskSensitiveData();
  }

  @Authorized()
  @Get("/{?params*}")
  public QueryResult<TerminologyServer> search(TerminologyServerQueryParams params) {
    QueryResult<TerminologyServer> query = serverService.query(params);
    if (!SessionStore.require().hasPrivilege("Server.edit")) {
      return query.map(TerminologyServer::publicView).map(TerminologyServer::maskSensitiveData);
    }
    return query.map(TerminologyServer::maskSensitiveData);
  }

  @Authorized("Server.edit")
  @Post("/resource")
  public TerminologyServerResourceResponse getResource(@Valid @Body TerminologyServerResourceRequest request) {
    return serverResourceService.getResource(request);
  }

  @Authorized("Server.view")
  @Get("/export/ecosystem")
  public HttpResponse<String> exportEcosystem(@QueryValue Optional<Boolean> download) {
    String json = serverService.exportToEcosystemFormat();
    MutableHttpResponse<String> response = HttpResponse.ok(json)
        .contentType(MediaType.APPLICATION_JSON);

    if (download.orElse(false)) {
      response.header(HttpHeaders.CONTENT_DISPOSITION,
          "attachment; filename=\"termx-servers.json\"");
    }

    return response;
  }

  @Authorized("Server.view")
  @Post("/{id}/authoritative/{type}/preview")
  public List<AuthoritativeResource> previewAuthoritative(@PathVariable Long id, @PathVariable String type,
                                                          @Body List<AuthoritativeResource> patterns) {
    return authoritativeService.findMatchingResources(type, patterns);
  }

  @Authorized("Server.view")
  @Get("/{id}/resources/{type}")
  public List<AuthoritativeResource> getMatchingResources(@PathVariable Long id, @PathVariable String type) {
    TerminologyServer server = serverService.load(id);
    if (server == null) {
      return List.of();
    }
    List<AuthoritativeResource> patterns = switch (type) {
      case "code-systems" -> server.getAuthoritative();
      case "value-sets" -> server.getAuthoritativeValuesets();
      case "concept-maps" -> server.getAuthoritativeConceptmaps();
      case "structure-definitions" -> server.getAuthoritativeStructuredefinitions();
      case "structure-maps" -> server.getAuthoritativeStructuremaps();
      default -> null;
    };
    if (patterns == null || patterns.isEmpty()) {
      return List.of();
    }
    return authoritativeService.findMatchingResources(type, patterns);
  }

  @Authorized("Server.edit")
  @Post("/import/ecosystem")
  public List<TerminologyServer> importEcosystem(@Body String json) {
    return serverService.importFromEcosystemFormat(json).stream()
        .map(TerminologyServer::maskSensitiveData)
        .toList();
  }
}
