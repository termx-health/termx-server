package com.kodality.termserver.ts.valueset;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetConcept;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetRuleSet;
import com.kodality.termserver.valueset.ValueSetVersion;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/ts/value-sets")
@RequiredArgsConstructor
public class ValueSetController {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;

  @Get(uri = "{?params*}")
  public QueryResult<ValueSet> getValueSets(ValueSetQueryParams params) {
    return valueSetService.query(params);
  }

  @Get(uri = "/{id}")
  public ValueSet getValueSet(@PathVariable String id) {
    return valueSetService.get(id).orElseThrow(() -> new NotFoundException("ValueSet not found: " + id));
  }

  @Post
  public HttpResponse<?> create(@Body @Valid ValueSet valueSet) {
    valueSetService.create(valueSet);
    return HttpResponse.created(valueSet);
  }

  @Get(uri = "/{valueSet}/versions")
  public List<ValueSetVersion> getValueSetVersions(@PathVariable String valueSet) {
    return valueSetVersionService.getVersions(valueSet);
  }

  @Get(uri = "/{valueSet}/versions/{version}")
  public ValueSetVersion getValueSetVersion(@PathVariable String valueSet, @PathVariable String version) {
    return valueSetVersionService.getVersion(valueSet, version).orElseThrow(() -> new NotFoundException("Value set version not found: " + version));
  }

  @Post(uri = "/{valueSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable String valueSet, @Body @Valid ValueSetVersion version) {
    version.setValueSet(valueSet);
    valueSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Put(uri = "/{valueSet}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable String valueSet, @PathVariable Long id, @Body @Valid ValueSetVersion version) {
    version.setId(id);
    version.setValueSet(valueSet);
    valueSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Post(uri = "/{valueSet}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String valueSet, @PathVariable String version) {
    valueSetVersionService.activate(valueSet, version);
    return HttpResponse.noContent();
  }

  @Post(uri = "/{valueSet}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable String valueSet, @PathVariable String version) {
    valueSetVersionService.retire(valueSet, version);
    return HttpResponse.noContent();
  }

  @Get(uri = "/{valueSet}/versions/{version}/concepts")
  public List<ValueSetConcept> getConcepts(@PathVariable String valueSet, @PathVariable String version) {
    return valueSetVersionService.getConcepts(valueSet, version);
  }

  @Post(uri = "/{valueSet}/versions/{version}/concepts")
  public HttpResponse<?> saveConcepts(@PathVariable String valueSet, @PathVariable String version, @Body ConceptRequest request) {
    valueSetVersionService.saveConcepts(valueSet, version, request.getConcepts());
    return HttpResponse.ok();
  }

  @Post(uri = "/expand")
  public List<ValueSetConcept> expand(@Body ValueSetRuleSet ruleSet) {
    return valueSetVersionService.expand(ruleSet);
  }

  @Get(uri = "/{valueSet}/expand")
  public List<ValueSetConcept> expand(@PathVariable String valueSet) {
    return valueSetVersionService.expand(valueSet);
  }

  @Get(uri = "/{valueSet}/versions/{version}/expand")
  public List<ValueSetConcept> expand(@PathVariable String valueSet, @PathVariable String version) {
    return valueSetVersionService.expand(valueSet, version);
  }

  @Getter
  @Setter
  private static class ConceptRequest {
    private List<ValueSetConcept> concepts;
  }
}
