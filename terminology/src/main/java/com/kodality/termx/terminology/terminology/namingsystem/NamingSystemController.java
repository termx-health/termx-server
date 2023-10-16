package com.kodality.termx.terminology.terminology.namingsystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.ts.namingsystem.NamingSystem;
import com.kodality.termx.ts.namingsystem.NamingSystemQueryParams;
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

  @Authorized(Privilege.NS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<NamingSystem> queryNamingSystems(NamingSystemQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.NS_VIEW));
    return namingSystemService.query(params);
  }

  @Authorized(Privilege.NS_VIEW)
  @Get(uri = "/{namingSystem}")
  public NamingSystem getNamingSystem(@PathVariable String namingSystem) {
    return namingSystemService.load(namingSystem).orElseThrow(() -> new NotFoundException("Naming system not found: " + namingSystem));
  }

  @Authorized(Privilege.NS_EDIT)
  @Post
  public HttpResponse<?> saveNamingSystem(@Body @Valid NamingSystem namingSystem) {
    SessionStore.require().checkPermitted(namingSystem.getId(), Privilege.NS_EDIT);
    namingSystemService.save(namingSystem);
    return HttpResponse.created(namingSystem);
  }

  @Authorized(Privilege.NS_PUBLISH)
  @Post(uri = "/{namingSystem}/activate")
  public HttpResponse<?> activateNamingSystem(@PathVariable String namingSystem) {
    namingSystemService.activate(namingSystem);
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.NS_PUBLISH)
  @Post(uri = "/{namingSystem}/retire")
  public HttpResponse<?> retireNamingSystem(@PathVariable String namingSystem) {
    namingSystemService.retire(namingSystem);
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.NS_PUBLISH)
  @Delete(uri = "/{namingSystem}")
  public HttpResponse<?> deleteNamingSystem(@PathVariable String namingSystem) {
    namingSystemService.cancel(namingSystem);
    return HttpResponse.ok();
  }
}
