package com.kodality.termserver.ts.codesystem.designation;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.DesignationQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/designations")
@RequiredArgsConstructor
public class DesignationController {
  private final DesignationService designationService;

  @Get(uri = "/{id}")
  public Designation getDesignation(@PathVariable Long id) {
    return designationService.get(id).orElseThrow(() -> new NotFoundException("Designation not found: " + id));
  }

  @Get(uri = "{?params*}")
  public QueryResult<Designation> getDesignations(DesignationQueryParams params) {
    return designationService.query(params);
  }

}
