package com.kodality.termx.sys.server;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceRequest;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/terminology-servers")
@RequiredArgsConstructor
public class TerminologyServerController {
  private final TerminologyServerService serverService;
  private final TerminologyServerResourceService serverResourceService;

  @Authorized("TerminologyServer.view")
  @Get("/kinds")
  public List<String> getKinds() {
    return serverService.getKinds();
  }

  @Authorized("TerminologyServer.edit")
  @Post
  public TerminologyServer create(@Valid @Body TerminologyServer ts) {
    ts.setId(null);
    return serverService.save(ts);
  }

  @Authorized("TerminologyServer.edit")
  @Put("/{id}")
  public TerminologyServer update(@PathVariable Long id, @Valid @Body TerminologyServer ts) {
    ts.setId(id);
    return serverService.save(ts);
  }

  @Authorized("TerminologyServer.edit")
  @Get("/{id}")
  public TerminologyServer load(@PathVariable Long id) {
    return serverService.load(id);
  }

  @Authorized()
  @Get("/{?params*}")
  public QueryResult<TerminologyServer> search(TerminologyServerQueryParams params) {
    QueryResult<TerminologyServer> query = serverService.query(params);
    if (!SessionStore.require().hasPrivilege("TerminologyServer.edit")) {
      return query.map(TerminologyServer::publicView);
    }
    return query;
  }

  @Authorized("TerminologyServer.edit")
  @Post("/resource")
  public TerminologyServerResourceResponse getResource(@Valid @Body TerminologyServerResourceRequest request) {
    return serverResourceService.getResource(request);
  }
}
