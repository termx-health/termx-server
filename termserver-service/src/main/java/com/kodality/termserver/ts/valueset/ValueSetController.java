package com.kodality.termserver.ts.valueset;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.ts.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
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
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;

  //----------------ValueSet----------------

  @Authorized("*.value-set.view")
  @Get(uri = "{?params*}")
  public QueryResult<ValueSet> queryValueSets(ValueSetQueryParams params) {
    return valueSetService.query(params);
  }

  @Authorized("*.value-set.view")
  @Get(uri = "/{valueSet}")
  public ValueSet getValueSet(@PathVariable String valueSet) {
    return valueSetService.load(valueSet).orElseThrow(() -> new NotFoundException("ValueSet not found: " + valueSet));
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
    return valueSetVersionService.load(valueSet, version).orElseThrow(() -> new NotFoundException("Value set version not found: " + version));
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

  //----------------ValueSet Version Concept----------------

  @Authorized("*.value-set.view")
  @Get(uri = "/{valueSet}/concepts/{id}")
  public ValueSetVersionConcept getConcept(@PathVariable String valueSet, @PathVariable Long id) {
    return valueSetVersionConceptService.load(id).orElseThrow(() -> new NotFoundException("ValueSet version concept not found: " + id));
  }

  @Authorized("*.value-set.edit")
  @Post(uri = "/{valueSet}/versions/{version}/concepts")
  public HttpResponse<?> createConcept(@PathVariable String valueSet, @PathVariable String version, @Body @Valid ValueSetVersionConcept concept) {
    concept.setId(null);
    valueSetVersionConceptService.save(concept, valueSet,version);
    return HttpResponse.created(concept);
  }

  @Authorized("*.value-set.edit")
  @Put(uri = "/{valueSet}/versions/{version}/concepts/{id}")
  public HttpResponse<?> updateConcept(@PathVariable String valueSet, @PathVariable String version, @PathVariable Long id, @Body @Valid ValueSetVersionConcept concept) {
    concept.setId(id);
    valueSetVersionConceptService.save(concept, valueSet, version);
    return HttpResponse.created(concept);
  }

  @Authorized("*.value-set.edit")
  @Delete(uri = "/{valueSet}/concepts/{id}")
  public HttpResponse<?> deleteConcept(@PathVariable String valueSet, @PathVariable Long id) {
    valueSetVersionConceptService.delete(id);
    return HttpResponse.ok();
  }

  @Authorized("*.value-set.view")
  @Post(uri = "/expand")
  public List<ValueSetVersionConcept> expand(@Body ValueSetExpandRequest request) {
    return valueSetVersionConceptService.expand(request.getValueSet(), request.getValueSetVersion(), request.getRuleSet());
  }


  //----------------ValueSet Version Rule----------------

  @Authorized("*.value-set.view")
  @Get(uri = "/{valueSet}/rules/{id}")
  public ValueSetVersionRule getRule(@PathVariable String valueSet, @PathVariable Long id) {
    return valueSetVersionRuleService.load(id).orElseThrow(() -> new NotFoundException("ValueSet version rule not found: " + id));
  }

  @Authorized("*.value-set.edit")
  @Post(uri = "/{valueSet}/rule-sets/{ruleSetId}/rules")
  public HttpResponse<?> createRule(@PathVariable String valueSet, @PathVariable Long ruleSetId, @Body @Valid ValueSetVersionRule rule) {
    rule.setId(null);
    valueSetVersionRuleService.save(rule, ruleSetId);
    return HttpResponse.created(rule);
  }

  @Authorized("*.value-set.edit")
  @Put(uri = "/{valueSet}/rule-sets/{ruleSetId}/rules/{id}")
  public HttpResponse<?> updateRule(@PathVariable String valueSet, @PathVariable Long ruleSetId, @PathVariable Long id, @Body @Valid ValueSetVersionRule rule) {
    rule.setId(id);
    valueSetVersionRuleService.save(rule, ruleSetId);
    return HttpResponse.created(rule);
  }

  @Authorized("*.value-set.edit")
  @Delete(uri = "/{valueSet}/rules/{id}")
  public HttpResponse<?> deleteRule(@PathVariable String valueSet, @PathVariable Long id) {
    valueSetVersionRuleService.delete(id);
    return HttpResponse.ok();
  }

  @Getter
  @Setter
  private static class ValueSetExpandRequest {
    private String valueSet;
    private String valueSetVersion;
    private ValueSetVersionRuleSet ruleSet;
  }
}
