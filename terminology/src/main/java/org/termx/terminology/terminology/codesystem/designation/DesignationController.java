package org.termx.terminology.terminology.codesystem.designation;

import com.kodality.commons.model.QueryResult;
import org.termx.terminology.Privilege;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.DesignationQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.RequiredArgsConstructor;

@Controller("/ts/designations")
@RequiredArgsConstructor
public class DesignationController {
  private final DesignationService designationService;

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<Designation> queryDesignations(DesignationQueryParams params) {
    params.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));
    return designationService.query(params);
  }
}
