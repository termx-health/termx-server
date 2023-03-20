package com.kodality.termserver.ts.namingsystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.ResourceId;
import com.kodality.termserver.auth.auth.UserPermissionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/naming-systems")
@RequiredArgsConstructor
public class NamingSystemController {
  private final NamingSystemService namingSystemService;
  private final UserPermissionService userPermissionService;

  @Authorized("*.NamingSystem.view")
  @Get(uri = "{?params*}")
  public QueryResult<NamingSystem> queryNamingSystems(NamingSystemQueryParams params) {
    params.setPermittedIds(userPermissionService.getPermittedResourceIds("NamingSystem", "view"));
    return namingSystemService.query(params);
  }

  @Authorized("*.NamingSystem.view")
  @Get(uri = "/{namingSystem}")
  public NamingSystem getNamingSystem(@PathVariable @ResourceId String namingSystem) {
    return namingSystemService.load(namingSystem).orElseThrow(() -> new NotFoundException("Naming system not found: " + namingSystem));
  }

  @Authorized("*.NamingSystem.edit")
  @Post
  public HttpResponse<?> saveNamingSystem(@Body @Valid NamingSystem namingSystem) {
    namingSystemService.save(namingSystem);
    return HttpResponse.created(namingSystem);
  }

  @Authorized("*.NamingSystem.publish")
  @Post(uri = "/{namingSystem}/activate")
  public HttpResponse<?> activateNamingSystem(@PathVariable @ResourceId String namingSystem) {
    namingSystemService.activate(namingSystem);
    return HttpResponse.noContent();
  }

  @Authorized("*.NamingSystem.publish")
  @Post(uri = "/{namingSystem}/retire")
  public HttpResponse<?> retireNamingSystem(@PathVariable @ResourceId String namingSystem) {
    namingSystemService.retire(namingSystem);
    return HttpResponse.noContent();
  }

  @Authorized("*.NamingSystem.publish")
  @Delete(uri = "/{namingSystem}")
  public HttpResponse<?> deleteNamingSystem(@PathVariable @ResourceId String namingSystem) {
    namingSystemService.cancel(namingSystem);
    return HttpResponse.ok();
  }
}
