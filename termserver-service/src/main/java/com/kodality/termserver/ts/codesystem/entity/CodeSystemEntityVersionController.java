package com.kodality.termserver.ts.codesystem.entity;

import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemSupplement;
import com.kodality.termserver.ts.codesystem.supplement.CodeSystemSupplementService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-system-entity-versions")
@RequiredArgsConstructor
public class CodeSystemEntityVersionController {
  private final CodeSystemSupplementService codeSystemSupplementService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Get(uri = "/{id}")
  public CodeSystemEntityVersion getEntityVersion(@PathVariable Long id) {
    return codeSystemEntityVersionService.load(id);
  }

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

  @Post(uri = "/{id}/supplements")
  public HttpResponse<?> createSupplement(@PathVariable Long id, @Body @Valid CodeSystemSupplement supplement) {
    codeSystemSupplementService.save(supplement, id);
    return HttpResponse.created(supplement);
  }
}
