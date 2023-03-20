package com.kodality.termserver.auth.privilege;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.Privilege;
import com.kodality.termserver.auth.PrivilegeQueryParams;
import com.kodality.termserver.auth.Authorized;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/auth/privileges")
@RequiredArgsConstructor
public class PrivilegeController {
  private final PrivilegeService privilegeService;

  @Authorized("*.Privilege.view")
  @Get(uri = "{?params*}")
  public QueryResult<Privilege> queryPrivileges(PrivilegeQueryParams params) {
    return privilegeService.query(params);
  }

  @Authorized("*.Privilege.view")
  @Get(uri = "/{id}")
  public Privilege getPrivilege(@PathVariable Long id) {
    return privilegeService.load(id);
  }

  @Authorized("*.Privilege.edit")
  @Post
  public HttpResponse<?> createPrivilege(@Body @Valid Privilege privilege) {
    privilegeService.save(privilege);
    return HttpResponse.created(privilege);
  }

  @Authorized("*.Privilege.edit")
  @Put("/{id}")
  public HttpResponse<?> updatePrivilege(@PathVariable Long id, @Body @Valid Privilege privilege) {
    privilege.setId(id);
    privilegeService.save(privilege);
    return HttpResponse.created(privilege);
  }

  @Authorized("*.Privilege.edit")
  @Delete("/{id}")
  public HttpResponse<?> deletePrivilege(@PathVariable Long id) {
    privilegeService.delete(id);
    return HttpResponse.ok();
  }
}
