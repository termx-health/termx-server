package com.kodality.termserver.auth.privilege;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.Privilege;
import com.kodality.termserver.auth.PrivilegeQueryParams;
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

  @Get(uri = "/{id}")
  public Privilege load(@PathVariable Long id) {
    return privilegeService.load(id);
  }

  @Get(uri = "{?params*}")
  public QueryResult<Privilege> query(PrivilegeQueryParams params) {
    return privilegeService.query(params);
  }

  @Post
  public HttpResponse<?> create(@Body @Valid Privilege privilege) {
    privilegeService.save(privilege);
    return HttpResponse.created(privilege);
  }

  @Put("/{id}")
  public HttpResponse<?> update(@PathVariable Long id, @Body @Valid Privilege privilege) {
    privilege.setId(id);
    privilegeService.save(privilege);
    return HttpResponse.created(privilege);
  }

  @Delete("/{id}")
  public HttpResponse<?> delete(@PathVariable Long id) {
    privilegeService.delete(id);
    return HttpResponse.ok();
  }
}
