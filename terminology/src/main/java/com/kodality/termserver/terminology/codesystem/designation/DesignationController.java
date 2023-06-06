package com.kodality.termserver.terminology.codesystem.designation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.DesignationQueryParams;
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
    return designationService.query(params);
  }

}
