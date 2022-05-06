package com.kodality.termserver.ts.mapset.entity;

import com.kodality.termserver.mapset.MapSetEntityVersion;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/map-set-entities")
@RequiredArgsConstructor
public class MapSetEntityController {
  private final MapSetEntityVersionService mapSetEntityVersionService;

  @Post(uri = "/{id}/versions")
  public HttpResponse<?> createVersion(@PathVariable Long id, @Body @Valid MapSetEntityVersion version) {
    version.setId(null);
    mapSetEntityVersionService.save(version, id);
    return HttpResponse.created(version);
  }

  @Put(uri = "/{id}/versions/{versionId}")
  public HttpResponse<?> updateVersion(@PathVariable Long id, @PathVariable Long versionId, @Body @Valid MapSetEntityVersion version) {
    version.setId(versionId);
    mapSetEntityVersionService.save(version, id);
    return HttpResponse.created(version);
  }
}
