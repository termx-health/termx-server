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
  public QueryResult<NamingSystem> getNamingSystems(NamingSystemQueryParams params) {
    return namingSystemService.query(params);
  }


  @Get(uri = "/{id}")
  public NamingSystem getNamingSystem(@PathVariable String id) {
    return namingSystemService.get(id).orElseThrow(() -> new NotFoundException("Naming system not found: " + id));
  }

  @Post
  public HttpResponse<?> create(@Body @Valid NamingSystem namingSystem) {
    namingSystemService.create(namingSystem);
    return HttpResponse.created(namingSystem);
  }
}
