package org.termx.uam.privilege;

import com.kodality.commons.model.QueryResult;
import org.termx.auth.Privilege;
import org.termx.auth.PrivilegeQueryParams;
import org.termx.core.auth.Authorized;
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

import static org.termx.uam.Privileges.P_WRITE;
import static org.termx.uam.Privileges.P_READ;

@Controller("/uam/privileges")
@RequiredArgsConstructor
public class PrivilegeController {
  private final PrivilegeService privilegeService;

  @Authorized(P_READ)
  @Get("{?params*}")
  public QueryResult<Privilege> query(PrivilegeQueryParams params) {
    return privilegeService.query(params);
  }

  @Authorized(P_READ)
  @Get("/{id}")
  public Privilege load(@PathVariable Long id) {
    return privilegeService.load(id);
  }

  @Authorized(P_WRITE)
  @Post
  public HttpResponse<?> create(@Body @Valid Privilege privilege) {
    privilegeService.save(privilege);
    return HttpResponse.created(privilege);
  }

  @Authorized(P_WRITE)
  @Put("/{id}")
  public HttpResponse<?> update(@PathVariable Long id, @Body @Valid Privilege privilege) {
    privilege.setId(id);
    privilegeService.save(privilege);
    return HttpResponse.created(privilege);
  }

  @Authorized(P_WRITE)
  @Delete("/{id}")
  public HttpResponse<?> delete(@PathVariable Long id) {
    privilegeService.delete(id);
    return HttpResponse.ok();
  }
}
