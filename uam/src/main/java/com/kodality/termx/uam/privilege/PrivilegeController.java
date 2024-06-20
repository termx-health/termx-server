package com.kodality.termx.uam.privilege;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Privilege;
import com.kodality.termx.auth.PrivilegeQueryParams;
import com.kodality.termx.core.auth.Authorized;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import static com.kodality.termx.uam.Privileges.P_EDIT;
import static com.kodality.termx.uam.Privileges.P_VIEW;

@Controller("/uam/privileges")
@RequiredArgsConstructor
public class PrivilegeController {
  private final PrivilegeService privilegeService;

  @Authorized(P_VIEW)
  @Get("{?params*}")
  public QueryResult<Privilege> query(PrivilegeQueryParams params) {
    return privilegeService.query(params);
  }

  @Authorized(P_VIEW)
  @Get("/{id}")
  public Privilege load(@PathVariable Long id) {
    return privilegeService.load(id);
  }

  @Authorized(P_EDIT)
  @Post
  public HttpResponse<?> create(@Body @Valid Privilege privilege) {
    privilegeService.save(privilege);
    return HttpResponse.created(privilege);
  }

  @Authorized(P_EDIT)
  @Put("/{id}")
  public HttpResponse<?> update(@PathVariable Long id, @Body @Valid Privilege privilege) {
    privilege.setId(id);
    privilegeService.save(privilege);
    return HttpResponse.created(privilege);
  }

  @Authorized(P_EDIT)
  @Delete("/{id}")
  public HttpResponse<?> delete(@PathVariable Long id) {
    privilegeService.delete(id);
    return HttpResponse.ok();
  }
}
