package com.kodality.termserver.mapset.entity;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import lombok.RequiredArgsConstructor;

@Controller("/map-set-entity-versions")
@RequiredArgsConstructor
public class MapSetEntityVersionController {
  private final MapSetEntityVersionService mapSetEntityVersionService;

  @Post(uri = "/{id}/activate")
  public HttpResponse<?> activateVersion(@PathVariable Long id) {
    mapSetEntityVersionService.activate(id);
    return HttpResponse.noContent();
  }

  @Post(uri = "/{id}/retire")
  public HttpResponse<?> retireVersion(@PathVariable Long id) {
    mapSetEntityVersionService.retire(id);
    return HttpResponse.noContent();
  }
}
