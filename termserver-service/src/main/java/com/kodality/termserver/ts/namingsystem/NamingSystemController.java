package com.kodality.termserver.ts.namingsystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.namingsystem.NamingSystem;
import com.kodality.termserver.namingsystem.NamingSystemQueryParams;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/naming-systems")
@RequiredArgsConstructor
public class NamingSystemController {
  private final NamingSystemService namingSystemService;

  @Get(uri = "{?params*}")
  public QueryResult<NamingSystem> queryNamingSystems(NamingSystemQueryParams params) {
    return namingSystemService.query(params);
  }

  @Get(uri = "/{namingSystem}")
  public NamingSystem getNamingSystem(@PathVariable String namingSystem) {
    return namingSystemService.load(namingSystem).orElseThrow(() -> new NotFoundException("Naming system not found: " + namingSystem));
  }

  @Post
  public HttpResponse<?> saveNamingSystem(@Body @Valid NamingSystem namingSystem) {
    namingSystemService.save(namingSystem);
    return HttpResponse.created(namingSystem);
  }

  @Post(uri = "/{namingSystem}/activate")
  public HttpResponse<?> activateNamingSystem(@PathVariable String namingSystem) {
    namingSystemService.activate(namingSystem);
    return HttpResponse.noContent();
  }

  @Post(uri = "/{namingSystem}/retire")
  public HttpResponse<?> retireNamingSystem(@PathVariable String namingSystem) {
    namingSystemService.retire(namingSystem);
    return HttpResponse.noContent();
  }
}
