package com.kodality.termserver.ts.valueset;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetConcept;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetRuleSet;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
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

  //----------------ValueSet----------------

  @Authorized("*.value-set.view")
  @Get(uri = "{?params*}")
  public QueryResult<ValueSet> queryValueSets(ValueSetQueryParams params) {
    return valueSetService.query(params);
  }

  @Authorized("*.value-set.view")
  @Get(uri = "/{valueSet}")
  public ValueSet getValueSet(@PathVariable String valueSet) {
    return valueSetService.get(valueSet).orElseThrow(() -> new NotFoundException("ValueSet not found: " + valueSet));
  }

  @Authorized("*.value-set.edit")
  @Post
  public HttpResponse<?> saveValueSet(@Body @Valid ValueSet valueSet) {
    valueSetService.save(valueSet);
    return HttpResponse.created(valueSet);
  }

  //----------------ValueSet Version----------------

  @Authorized("*.value-set.view")
  @Get(uri = "/{valueSet}/versions{?params*}")
  public QueryResult<ValueSetVersion> queryValueSetVersions(@PathVariable String valueSet, ValueSetVersionQueryParams params) {
    params.setValueSet(valueSet);
    return valueSetVersionService.query(params);
  }

  @Authorized("*.value-set.view")
  @Get(uri = "/{valueSet}/versions/{version}")
  public ValueSetVersion getValueSetVersion(@PathVariable String valueSet, @PathVariable String version) {
    return valueSetVersionService.getVersion(valueSet, version).orElseThrow(() -> new NotFoundException("Value set version not found: " + version));
  }

  @Authorized("*.value-set.edit")
  @Post(uri = "/{valueSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable String valueSet, @Body @Valid ValueSetVersion version) {
    version.setId(null);
    version.setValueSet(valueSet);
    valueSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.value-set.edit")
  @Put(uri = "/{valueSet}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable String valueSet, @PathVariable Long id, @Body @Valid ValueSetVersion version) {
    version.setId(id);
    version.setValueSet(valueSet);
    valueSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.value-set.publish")
  @Post(uri = "/{valueSet}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String valueSet, @PathVariable String version) {
    valueSetVersionService.activate(valueSet, version);
    return HttpResponse.noContent();
  }

  @Authorized("*.value-set.publish")
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

  @Authorized("*.value-set.view")
  @Post(uri = "/expand")
  public List<ValueSetConcept> expand(@Body ValueSetExpandRequest request) {
    return valueSetVersionService.expand(request.getValueSet(), request.getValueSetVersion(), request.getRuleSet());
  }

  @Getter
  @Setter
  private static class ConceptRequest {
    private List<ValueSetConcept> concepts;
  }

  @Getter
  @Setter
  private static class ValueSetExpandRequest {
    private String valueSet;
    private String valueSetVersion;
    private ValueSetRuleSet ruleSet;
  }
}
