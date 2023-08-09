package com.kodality.termx.terminology.valueset;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ApiError;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.ResourceId;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.job.logger.ImportLogger;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termx.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetExpandRequest;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetTransactionRequest;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionReference;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/ts/value-sets")
@RequiredArgsConstructor
public class ValueSetController {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final ValueSetDuplicateService valueSetDuplicateService;
  private final ImportLogger importLogger;
  private final ValueSetProvenanceService provenanceService;

  private final UserPermissionService userPermissionService;

  //----------------ValueSet----------------

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<ValueSet> queryValueSets(ValueSetQueryParams params) {
    params.setPermittedIds(userPermissionService.getPermittedResourceIds("ValueSet", "view"));
    return valueSetService.query(params);
  }

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{valueSet}")
  public ValueSet getValueSet(@PathVariable @ResourceId String valueSet) {
    ValueSet vs = valueSetService.load(valueSet);
    if (vs == null) {
      throw new NotFoundException("ValueSet not found: " + valueSet);
    }
    return vs;
  }

  @Authorized(Privilege.CS_EDIT)
  @Post("/transaction")
  public HttpResponse<?> saveValueSetTransaction(@Body @Valid ValueSetTransactionRequest request) {
    provenanceService.provenanceValueSetTransaction("save", request, () -> {
      valueSetService.save(request);
    });
    return HttpResponse.created(request.getValueSet());
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{valueSet}/change-id")
  public HttpResponse<?> changeValueSetId(@PathVariable String valueSet, @Valid @Body Map<String, String> body) {
    String newId = body.get("id");
    valueSetService.changeId(valueSet, newId);
    provenanceService.create(new Provenance("change-id", "ValueSet", newId)
        .setChanges(Map.of("id", ProvenanceChange.of(valueSet, newId))));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.VS_PUBLISH)
  @Delete(uri = "/{valueSet}")
  public HttpResponse<?> deleteValueSet(@PathVariable @ResourceId String valueSet) {
    valueSetService.cancel(valueSet);
    provenanceService.create(new Provenance("delete", "ValueSet", valueSet));
    return HttpResponse.ok();
  }

  //----------------ValueSet Version----------------

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{valueSet}/versions{?params*}")
  public QueryResult<ValueSetVersion> queryValueSetVersions(@PathVariable @ResourceId String valueSet, ValueSetVersionQueryParams params) {
    params.setPermittedValueSets(userPermissionService.getPermittedResourceIds("ValueSet", "view"));
    params.setValueSet(valueSet);
    return valueSetVersionService.query(params);
  }

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{valueSet}/versions/{version}")
  public ValueSetVersion getValueSetVersion(@PathVariable @ResourceId String valueSet, @PathVariable String version) {
    return valueSetVersionService.load(valueSet, version).orElseThrow(() -> new NotFoundException("Value set version not found: " + version));
  }

  @Authorized(Privilege.VS_EDIT)
  @Post(uri = "/{valueSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable @ResourceId String valueSet, @Body @Valid ValueSetVersion version) {
    version.setId(null);
    version.setValueSet(valueSet);
    provenanceService.provenanceValueSetVersion("save", valueSet, version.getVersion(), () -> {
      valueSetVersionService.save(version);
    });
    return HttpResponse.created(version);
  }

  @Authorized(Privilege.VS_EDIT)
  @Put(uri = "/{valueSet}/versions/{version}")
  public HttpResponse<?> updateVersion(@PathVariable @ResourceId String valueSet, @PathVariable String version, @Body @Valid ValueSetVersion valueSetVersion) {
    valueSetVersion.setVersion(version);
    valueSetVersion.setValueSet(valueSet);
    provenanceService.provenanceValueSetVersion("save", valueSet, version, () -> {
      valueSetVersionService.save(valueSetVersion);
    });
    return HttpResponse.created(valueSetVersion);
  }

  @Authorized(Privilege.VS_PUBLISH)
  @Post(uri = "/{valueSet}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable @ResourceId String valueSet, @PathVariable String version) {
    provenanceService.provenanceValueSetVersion("activate", valueSet, version, () -> {
      valueSetVersionService.activate(valueSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.VS_PUBLISH)
  @Post(uri = "/{valueSet}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable @ResourceId String valueSet, @PathVariable String version) {
    provenanceService.provenanceValueSetVersion("retire", valueSet, version, () -> {
      valueSetVersionService.retire(valueSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.VS_PUBLISH)
  @Post(uri = "/{valueSet}/versions/{version}/draft")
  public HttpResponse<?> saveAsDraft(@PathVariable @ResourceId String valueSet, @PathVariable String version) {
    provenanceService.provenanceValueSetVersion("save", valueSet, version, () -> {
      valueSetVersionService.saveAsDraft(valueSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{valueSet}/versions/{version}/duplicate")
  public HttpResponse<?> duplicateValueSetVersion(@PathVariable @ResourceId String valueSet, @PathVariable String version,
                                                  @Body @Valid ValueSetVersionDuplicateRequest request) {
    provenanceService.provenanceValueSetVersion("duplicate", valueSet, request.getVersion(), () -> {
      valueSetDuplicateService.duplicateValueSetVersion(request.getVersion(), request.getValueSet(), version, valueSet);
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Delete(uri = "/{valueset}/versions/{version}")
  public HttpResponse<?> deleteValueSetVersion(@PathVariable @ResourceId String valueset, @PathVariable String version) {
    Long versionId = valueSetVersionService.load(valueset, version).map(ValueSetVersionReference::getId).orElseThrow();
    valueSetVersionService.cancel(versionId, valueset);
    provenanceService.create(new Provenance("deleted", "ValueSetVersion", versionId.toString(), version)
        .addContext("part-of", "ValueSet", valueset));
    return HttpResponse.ok();
  }

  //----------------ValueSet Version Concept----------------

  @Authorized(Privilege.VS_VIEW)
  @Post(uri = "/expand")
  public List<ValueSetVersionConcept> expand(@Body @Valid ValueSetExpandRequest request) {
    userPermissionService.checkPermitted(request.getValueSet(), "ValueSet", "view");
    return valueSetVersionConceptService.expand(request.getValueSet(), request.getValueSetVersion(), request.getRuleSet());
  }

  @Authorized(Privilege.VS_VIEW)
  @Post(uri = "/expand-async")
  public JobLogResponse expandAsync(@Body @Valid ValueSetExpandRequest request) {
    JobLogResponse jobLogResponse = importLogger.createJob(request.getValueSet(), "value-set-expand");
    userPermissionService.checkPermitted(request.getValueSet(), "ValueSet", "view");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("ValueSet '{}' expand started", request.getValueSet());
        long start = System.currentTimeMillis();
        valueSetVersionConceptService.expand(request.getValueSet(), request.getValueSetVersion(), request.getRuleSet());
        log.info("ValueSet '{}' expand  took {}", request.getValueSet(), (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while ValueSet '{}' expand", request.getValueSet(), e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while ValueSet '{}' expand", request.getValueSet(), e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE307.toApiException());
      }
    }));
    return jobLogResponse;
  }

  //----------------ValueSet Version Rule----------------
  @Authorized(Privilege.VS_EDIT)
  @Post(uri = "/{valueSet}/versions/{version}/rules")
  public HttpResponse<?> createRule(@PathVariable @ResourceId String valueSet, @PathVariable String version, @Body @Valid ValueSetVersionRule rule) {
    rule.setId(null);
    provenanceService.provenanceValueSetVersion("save-rules", valueSet, version, () -> {
      valueSetVersionRuleService.save(rule, valueSet, version);
    });
    return HttpResponse.created(rule);
  }

  @Authorized(Privilege.VS_EDIT)
  @Put(uri = "/{valueSet}/versions/{version}/rules/{id}")
  public HttpResponse<?> updateRule(@PathVariable @ResourceId String valueSet, @PathVariable String version, @PathVariable Long id,
                                    @Body @Valid ValueSetVersionRule rule) {
    rule.setId(id);
    provenanceService.provenanceValueSetVersion("save-rules", valueSet, version, () -> {
      valueSetVersionRuleService.save(rule, valueSet, version);
    });
    return HttpResponse.created(rule);
  }

  @Authorized(Privilege.VS_EDIT)
  @Delete(uri = "/{valueSet}/versions/{version}/rules/{id}")
  public HttpResponse<?> deleteRule(@PathVariable @ResourceId String valueSet, @PathVariable String version, @PathVariable Long id) {
    provenanceService.provenanceValueSetVersion("delete-rules", valueSet, version, () -> {
      valueSetVersionRuleService.delete(id, valueSet);
    });
    return HttpResponse.ok();
  }

  @Getter
  @Setter
  @Introspected
  public static class ValueSetVersionDuplicateRequest {
    private String valueSet;
    private String version;
  }
}
