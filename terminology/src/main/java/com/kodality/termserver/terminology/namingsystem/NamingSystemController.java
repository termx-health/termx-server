package com.kodality.termserver.terminology.namingsystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.ResourceId;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.ts.namingsystem.NamingSystem;
import com.kodality.termserver.ts.namingsystem.NamingSystemQueryParams;
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

  @Authorized(Privilege.NS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<NamingSystem> queryNamingSystems(NamingSystemQueryParams params) {
    params.setPermittedIds(userPermissionService.getPermittedResourceIds("NamingSystem", "view"));
    return namingSystemService.query(params);
  }

  @Authorized(Privilege.NS_VIEW)
  @Get(uri = "/{namingSystem}")
  public NamingSystem getNamingSystem(@PathVariable @ResourceId String namingSystem) {
    return namingSystemService.load(namingSystem).orElseThrow(() -> new NotFoundException("Naming system not found: " + namingSystem));
  }

  @Authorized(Privilege.NS_EDIT)
  @Post
  public HttpResponse<?> saveNamingSystem(@Body @Valid NamingSystem namingSystem) {
    namingSystemService.save(namingSystem);
    return HttpResponse.created(namingSystem);
  }

  @Authorized(Privilege.NS_PUBLISH)
  @Post(uri = "/{namingSystem}/activate")
  public HttpResponse<?> activateNamingSystem(@PathVariable @ResourceId String namingSystem) {
    namingSystemService.activate(namingSystem);
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.NS_PUBLISH)
  @Post(uri = "/{namingSystem}/retire")
  public HttpResponse<?> retireNamingSystem(@PathVariable @ResourceId String namingSystem) {
    namingSystemService.retire(namingSystem);
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.NS_PUBLISH)
  @Delete(uri = "/{namingSystem}")
  public HttpResponse<?> deleteNamingSystem(@PathVariable @ResourceId String namingSystem) {
    namingSystemService.cancel(namingSystem);
    return HttpResponse.ok();
  }
}
