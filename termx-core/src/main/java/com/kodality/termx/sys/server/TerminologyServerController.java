package com.kodality.termx.sys.server;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceRequest;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceResponse;
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

  @Authorized(Privilege.P_EDIT)
  @Post
  public TerminologyServer create(@Valid @Body TerminologyServer ts) {
    ts.setId(null);
    return terminologyServerService.save(ts);
  }

  @Authorized(Privilege.P_EDIT)
  @Put("/{id}")
  public TerminologyServer update(@Parameter Long id, @Valid @Body TerminologyServer ts) {
    ts.setId(id);
    return terminologyServerService.save(ts);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/{id}")
  public TerminologyServer load(@Parameter Long id) {
    return terminologyServerService.load(id);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/{?params*}")
  public QueryResult<TerminologyServer> search(TerminologyServerQueryParams params) {
    return terminologyServerService.query(params);
  }

  @Authorized(Privilege.P_VIEW)
  @Post("/resource")
  public TerminologyServerResourceResponse getResource(@Valid @Body TerminologyServerResourceRequest request) {
    return terminologyServerResourceService.getResource(request);
  }
}
