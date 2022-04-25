package com.kodality.termserver.codesystem.entity;

import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/code-system-entities")
@RequiredArgsConstructor
public class CodeSystemEntityController {
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Post(uri = "/{id}/versions")
  public HttpResponse<?> createVersion(@PathVariable Long id, @Body @Valid CodeSystemEntityVersion version) {
    version.setId(null);
    codeSystemEntityVersionService.save(version, id);
    return HttpResponse.created(version);
  }

  @Put(uri = "/{id}/versions/{versionId}")
  public HttpResponse<?> updateVersion(@PathVariable Long id, @PathVariable Long versionId, @Body @Valid CodeSystemEntityVersion version) {
    version.setId(versionId);
    codeSystemEntityVersionService.save(version, id);
    return HttpResponse.created(version);
  }
}
