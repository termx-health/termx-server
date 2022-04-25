package com.kodality.termserver.codesystem.entity;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import lombok.RequiredArgsConstructor;

@Controller("/code-system-entity-versions")
@RequiredArgsConstructor
public class CodeSystemEntityVersionController {
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Post(uri = "/{id}/activate")
  public HttpResponse<?> activateVersion(@PathVariable Long id) {
    codeSystemEntityVersionService.activate(id);
    return HttpResponse.noContent();
  }

  @Post(uri = "/{id}/retire")
  public HttpResponse<?> retireVersion(@PathVariable Long id) {
    codeSystemEntityVersionService.retire(id);
    return HttpResponse.noContent();
  }
}
