package com.kodality.termserver.codesystem;

import com.kodality.termserver.codesystem.concept.ConceptService;
import com.kodality.termserver.commons.model.exception.NotFoundException;
import com.kodality.termserver.commons.model.model.QueryResult;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/code-systems")
@RequiredArgsConstructor
public class CodeSystemController {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;

  @Get(uri = "{?params*}")
  public QueryResult<CodeSystem> getCodeSystems(CodeSystemQueryParams params) {
    return codeSystemService.query(params);
  }

  @Get(uri = "/{codeSystem}")
  public CodeSystem getCodeSystem(@PathVariable String codeSystem) {
    return codeSystemService.get(codeSystem).orElseThrow(() -> new NotFoundException("CodeSystem not found: " + codeSystem));
  }

  @Get(uri = "/{codeSystem}/versions/{version}")
  public CodeSystemVersion getCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version) {
    return codeSystemVersionService.getVersion(codeSystem, version).orElseThrow(() -> new NotFoundException("CodeSystem version not found: " + codeSystem));
  }

  @Get(uri = "/{codeSystem}/versions/{version}/concepts{?params*}")
  public QueryResult<Concept> getConcepts(@PathVariable String codeSystem, @PathVariable String version, ConceptQueryParams params) {
    params.setCodeSystem(codeSystem);
    params.setVersion(version);
    return conceptService.query(params);
  }

  @Get(uri = "/{codeSystem}/versions/{versionCode}/concepts/{conceptCode}")
  public Concept getConcept(@PathVariable String codeSystem, @PathVariable String versionCode, @PathVariable String conceptCode) {
    return conceptService.get(codeSystem, versionCode, conceptCode).orElseThrow(() -> new NotFoundException("Concept not found: " + conceptCode));
  }

  @Get(uri = "/{codeSystem}/concepts{?params*}")
  public QueryResult<Concept> getConcepts(@PathVariable String codeSystem, ConceptQueryParams params) {
    params.setCodeSystem(codeSystem);
    return conceptService.query(params);
  }

  @Get(uri = "/{codeSystem}/concepts/{code}")
  public Concept get(@PathVariable String codeSystem, @PathVariable String code) {
    return conceptService.get(codeSystem, code).orElseThrow(() -> new NotFoundException("Concept not found: " + code));
  }
}
