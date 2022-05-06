package com.kodality.termserver.ts.codesystem.concept;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.termserver.codesystem.Concept;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/concepts")
@RequiredArgsConstructor
public class ConceptController {

  private final ConceptService conceptService;

  @Get(uri = "/{id}")
  public Concept getConcept(@PathVariable Long id) {
    return conceptService.get(id).orElseThrow(() -> new NotFoundException("Concept not found: " + id));
  }
}
