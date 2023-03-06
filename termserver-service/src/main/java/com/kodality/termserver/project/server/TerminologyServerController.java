package com.kodality.termserver.project.server;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/terminology-servers")
@RequiredArgsConstructor
public class TerminologyServerController {
  private final TerminologyServerService terminologyServerService;
  private final TerminologyServerResourceService terminologyServerResourceService;

  @Authorized("*.TerminologyServer.edit")
  @Post()
  public TerminologyServer create(@Valid @Body TerminologyServer ts) {
    ts.setId(null);
    return terminologyServerService.save(ts);
  }

  @Authorized("*.TerminologyServer.edit")
  @Put("{id}")
  public TerminologyServer update(@Parameter Long id, @Valid @Body TerminologyServer ts) {
    ts.setId(id);
    return terminologyServerService.save(ts);
  }

  @Authorized("*.TerminologyServer.view")
  @Get("{id}")
  public TerminologyServer load(@Parameter Long id) {
    return terminologyServerService.load(id);
  }

  @Authorized("*.TerminologyServer.view")
  @Get("/{?params*}")
  public QueryResult<TerminologyServer> search(TerminologyServerQueryParams params) {
    return terminologyServerService.query(params);
  }

  @Authorized("*.TerminologyServer.view")
  @Post("/resource")
  public TerminologyServerResourceResponse getResource(@Valid @Body TerminologyServerResourceRequest request) {
    return terminologyServerResourceService.getResource(request);
  }
}
