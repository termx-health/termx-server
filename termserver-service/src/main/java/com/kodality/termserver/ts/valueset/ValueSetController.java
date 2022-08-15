package com.kodality.termserver.ts.valueset;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.ResourceId;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.ts.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
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

  private final UserPermissionService userPermissionService;

  //----------------ValueSet----------------

  @Authorized("*.ValueSet.view")
  @Get(uri = "{?params*}")
  public QueryResult<ValueSet> queryValueSets(ValueSetQueryParams params) {
    params.setPermittedIds(userPermissionService.getPermittedResourceIds("ValueSet", "view"));
    return valueSetService.query(params);
  }

  @Authorized("*.ValueSet.view")
  @Get(uri = "/{valueSet}")
  public ValueSet getValueSet(@PathVariable @ResourceId String valueSet) {
    return valueSetService.load(valueSet).orElseThrow(() -> new NotFoundException("ValueSet not found: " + valueSet));
  }

  @Authorized("*.ValueSet.edit")
  @Post
  public HttpResponse<?> saveValueSet(@Body @Valid ValueSet valueSet) {
    valueSetService.save(valueSet);
    return HttpResponse.created(valueSet);
  }

  //----------------ValueSet Version----------------

  @Authorized("*.ValueSet.view")
  @Get(uri = "/{valueSet}/versions{?params*}")
  public QueryResult<ValueSetVersion> queryValueSetVersions(@PathVariable @ResourceId String valueSet, ValueSetVersionQueryParams params) {
    params.setPermittedValueSets(userPermissionService.getPermittedResourceIds("ValueSet", "view"));
    params.setValueSet(valueSet);
    return valueSetVersionService.query(params);
  }

  @Authorized("*.ValueSet.view")
  @Get(uri = "/{valueSet}/versions/{version}")
  public ValueSetVersion getValueSetVersion(@PathVariable @ResourceId String valueSet, @PathVariable String version) {
    return valueSetVersionService.load(valueSet, version).orElseThrow(() -> new NotFoundException("Value set version not found: " + version));
  }

  @Authorized("*.ValueSet.edit")
  @Post(uri = "/{valueSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable @ResourceId String valueSet, @Body @Valid ValueSetVersion version) {
    version.setId(null);
    version.setValueSet(valueSet);
    valueSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.ValueSet.edit")
  @Put(uri = "/{valueSet}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable @ResourceId String valueSet, @PathVariable Long id, @Body @Valid ValueSetVersion version) {
    version.setId(id);
    version.setValueSet(valueSet);
    valueSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.ValueSet.publish")
  @Post(uri = "/{valueSet}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable @ResourceId String valueSet, @PathVariable String version) {
    valueSetVersionService.activate(valueSet, version);
    return HttpResponse.noContent();
  }

  @Authorized("*.ValueSet.publish")
  @Post(uri = "/{valueSet}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable @ResourceId String valueSet, @PathVariable String version) {
    valueSetVersionService.retire(valueSet, version);
    return HttpResponse.noContent();
  }

  //----------------ValueSet Version Concept----------------

  @Authorized("*.ValueSet.view")
  @Get(uri = "/{valueSet}/concepts/{id}")
  public ValueSetVersionConcept getConcept(@PathVariable @ResourceId String valueSet, @PathVariable Long id) {
    return valueSetVersionConceptService.load(id).orElseThrow(() -> new NotFoundException("ValueSet version concept not found: " + id));
  }

  @Authorized("*.ValueSet.edit")
  @Post(uri = "/{valueSet}/versions/{version}/concepts")
  public HttpResponse<?> createConcept(@PathVariable @ResourceId String valueSet, @PathVariable String version, @Body @Valid ValueSetVersionConcept concept) {
    concept.setId(null);
    valueSetVersionConceptService.save(concept, valueSet, version);
    return HttpResponse.created(concept);
  }

  @Authorized("*.ValueSet.edit")
  @Put(uri = "/{valueSet}/versions/{version}/concepts/{id}")
  public HttpResponse<?> updateConcept(@PathVariable @ResourceId String valueSet, @PathVariable String version, @PathVariable Long id, @Body @Valid ValueSetVersionConcept concept) {
    concept.setId(id);
    valueSetVersionConceptService.save(concept, valueSet, version);
    return HttpResponse.created(concept);
  }

  @Authorized("*.ValueSet.edit")
  @Delete(uri = "/{valueSet}/concepts/{id}")
  public HttpResponse<?> deleteConcept(@PathVariable @ResourceId String valueSet, @PathVariable Long id) {
    valueSetVersionConceptService.delete(id, valueSet);
    return HttpResponse.ok();
  }

  @Authorized("*.ValueSet.view")
  @Post(uri = "/expand")
  public List<ValueSetVersionConcept> expand(@Body ValueSetExpandRequest request) {
    userPermissionService.checkPermitted(request.getValueSet(), "ValueSet", "view");
    return valueSetVersionConceptService.expand(request.getValueSet(), request.getValueSetVersion(), request.getRuleSet());
  }

  //----------------ValueSet Version Rule----------------

  @Authorized("*.ValueSet.view")
  @Get(uri = "/{valueSet}/rules/{id}")
  public ValueSetVersionRule getRule(@PathVariable @ResourceId String valueSet, @PathVariable Long id) {
    return valueSetVersionRuleService.load(id).orElseThrow(() -> new NotFoundException("ValueSet version rule not found: " + id));
  }

  @Authorized("*.ValueSet.edit")
  @Post(uri = "/{valueSet}/rule-sets/{ruleSetId}/rules")
  public HttpResponse<?> createRule(@PathVariable @ResourceId String valueSet, @PathVariable Long ruleSetId, @Body @Valid ValueSetVersionRule rule) {
    rule.setId(null);
    valueSetVersionRuleService.save(rule, ruleSetId, valueSet);
    return HttpResponse.created(rule);
  }

  @Authorized("*.ValueSet.edit")
  @Put(uri = "/{valueSet}/rule-sets/{ruleSetId}/rules/{id}")
  public HttpResponse<?> updateRule(@PathVariable @ResourceId String valueSet, @PathVariable Long ruleSetId, @PathVariable Long id, @Body @Valid ValueSetVersionRule rule) {
    rule.setId(id);
    valueSetVersionRuleService.save(rule, ruleSetId, valueSet);
    return HttpResponse.created(rule);
  }

  @Authorized("*.ValueSet.edit")
  @Delete(uri = "/{valueSet}/rules/{id}")
  public HttpResponse<?> deleteRule(@PathVariable @ResourceId String valueSet, @PathVariable Long id) {
    valueSetVersionRuleService.delete(id, valueSet);
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
