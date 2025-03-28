package com.kodality.termx.terminology.terminology.valueset;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.terminology.terminology.valueset.expansion.ValueSetExportService;
import com.kodality.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService;
import com.kodality.termx.terminology.terminology.valueset.provenance.ValueSetProvenanceService;
import com.kodality.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleSetService;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetExpandRequest;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetTransactionRequest;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionReference;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import jakarta.validation.Valid;
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
  private final ValueSetVersionRuleSetService valueSetVersionRuleSetService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final ValueSetDuplicateService valueSetDuplicateService;
  private final ImportLogger importLogger;
  private final ValueSetProvenanceService provenanceService;
  private final LorqueProcessService lorqueProcessService;
  private final ValueSetExportService valueSetExportService;

  //----------------ValueSet----------------

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<ValueSet> queryValueSets(ValueSetQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.VS_VIEW));
    return valueSetService.query(params);
  }

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{valueSet}{?decorate}")
  public ValueSet getValueSet(@PathVariable String valueSet, Optional<Boolean> decorate) {
    return valueSetService.load(valueSet, decorate.orElse(false))
        .orElseThrow(() -> new NotFoundException("ValueSet not found: " + valueSet));
  }

  @Authorized(Privilege.VS_EDIT)
  @Post("/transaction")
  public HttpResponse<?> saveValueSetTransaction(@Body @Valid ValueSetTransactionRequest request) {
    SessionStore.require().checkPermitted(request.getValueSet().getId(), Privilege.VS_EDIT);
    provenanceService.provenanceValueSetTransaction("save", request, () -> {
      valueSetService.save(request);
    });
    return HttpResponse.created(request.getValueSet());
  }

  @Authorized(Privilege.VS_EDIT)
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
  public HttpResponse<?> deleteValueSet(@PathVariable String valueSet) {
    valueSetService.cancel(valueSet);
    provenanceService.create(new Provenance("delete", "ValueSet", valueSet));
    return HttpResponse.ok();
  }

  //----------------ValueSet Version----------------

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{valueSet}/versions{?params*}")
  public QueryResult<ValueSetVersion> queryValueSetVersions(@PathVariable String valueSet, ValueSetVersionQueryParams params) {
    params.setValueSet(valueSet);
    return valueSetVersionService.query(params);
  }

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{valueSet}/versions/{version}")
  public ValueSetVersion getValueSetVersion(@PathVariable String valueSet, @PathVariable String version) {
    return valueSetVersionService.load(valueSet, version)
        .orElseThrow(() -> new NotFoundException("Value set version not found: " + version));
  }

  @Authorized(Privilege.VS_EDIT)
  @Post(uri = "/{valueSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable String valueSet, @Body @Valid ValueSetVersion version) {
    version.setId(null);
    version.setValueSet(valueSet);
    provenanceService.provenanceValueSetVersion("save", valueSet, version.getVersion(), () -> {
      valueSetVersionService.save(version);
    });
    return HttpResponse.created(version);
  }

  @Authorized(Privilege.VS_EDIT)
  @Put(uri = "/{valueSet}/versions/{version}")
  public HttpResponse<?> updateVersion(@PathVariable String valueSet, @PathVariable String version, @Body @Valid ValueSetVersion valueSetVersion) {
    valueSetVersion.setVersion(version);
    valueSetVersion.setValueSet(valueSet);
    provenanceService.provenanceValueSetVersion("save", valueSet, version, () -> {
      valueSetVersionService.save(valueSetVersion);
    });
    return HttpResponse.created(valueSetVersion);
  }

  @Authorized(Privilege.VS_PUBLISH)
  @Post(uri = "/{valueSet}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String valueSet, @PathVariable String version) {
    provenanceService.provenanceValueSetVersion("activate", valueSet, version, () -> {
      valueSetVersionService.activate(valueSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.VS_PUBLISH)
  @Post(uri = "/{valueSet}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable String valueSet, @PathVariable String version) {
    provenanceService.provenanceValueSetVersion("retire", valueSet, version, () -> {
      valueSetVersionService.retire(valueSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.VS_PUBLISH)
  @Post(uri = "/{valueSet}/versions/{version}/draft")
  public HttpResponse<?> saveAsDraft(@PathVariable String valueSet, @PathVariable String version) {
    provenanceService.provenanceValueSetVersion("save", valueSet, version, () -> {
      valueSetVersionService.saveAsDraft(valueSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.VS_EDIT)
  @Post(uri = "/{valueSet}/versions/{version}/duplicate")
  public HttpResponse<?> duplicateValueSetVersion(@PathVariable String valueSet, @PathVariable String version,
                                                  @Body @Valid ValueSetVersionDuplicateRequest request) {
    SessionStore.require().checkPermitted(request.getValueSet(), Privilege.VS_EDIT);
    provenanceService.provenanceValueSetVersion("duplicate", valueSet, request.getVersion(), () -> {
      valueSetDuplicateService.duplicateValueSetVersion(request.getVersion(), request.getValueSet(), version, valueSet);
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.VS_PUBLISH)
  @Delete(uri = "/{valueset}/versions/{version}")
  public HttpResponse<?> deleteValueSetVersion(@PathVariable String valueset, @PathVariable String version) {
    Long versionId = valueSetVersionService.load(valueset, version).map(ValueSetVersionReference::getId).orElseThrow();
    valueSetVersionService.cancel(versionId);
    provenanceService.create(new Provenance("deleted", "ValueSetVersion", versionId.toString(), version)
        .addContext("part-of", "ValueSet", valueset));
    return HttpResponse.ok();
  }

  //----------------ValueSet Expand----------------

  @Authorized(Privilege.VS_VIEW)
  @Post(uri = "/expand")
  public List<ValueSetVersionConcept> expand(@Body @Valid ValueSetExpandRequest request) {
    SessionStore.require().checkPermitted(request.getValueSet(), Privilege.VS_VIEW);
    return valueSetVersionConceptService.expand(request.getValueSet(), request.getValueSetVersion());
  }

  @Authorized(Privilege.VS_VIEW)
  @Post(uri = "/expand-async")
  public JobLogResponse expandAsync(@Body @Valid ValueSetExpandRequest request) {
    SessionStore.require().checkPermitted(request.getValueSet(), Privilege.VS_VIEW);
    JobLogResponse jobLogResponse = importLogger.createJob(request.getValueSet(), "value-set-expand");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("ValueSet '{}' expand started", request.getValueSet());
        long start = System.currentTimeMillis();
        valueSetVersionConceptService.expand(request.getValueSet(), request.getValueSetVersion());
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

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{valueSet}/versions/{version}/expansion-export{?params*}")
  public LorqueProcess exportConcepts(@PathVariable String valueSet, @PathVariable String version, Map<String, String> params) {
    return valueSetExportService.export(valueSet, version, params.getOrDefault("format", "csv"));
  }

  @Authorized(Privilege.VS_VIEW)
  @Get(value = "/expansion-export-csv/result/{lorqueProcessId}", produces = "application/csv")
  public HttpResponse<?> getConceptExportCSV(Long lorqueProcessId) {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(lorqueProcessService.load(lorqueProcessId).getResult());
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
        .contentType(MediaType.of("application/csv"));
  }

  @Authorized(Privilege.VS_VIEW)
  @Get(value = "/expansion-export-xlsx/result/{lorqueProcessId}", produces = "application/vnd.ms-excel")
  public HttpResponse<?> getConceptExportXLSX(Long lorqueProcessId) {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(lorqueProcessService.load(lorqueProcessId).getResult());
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
        .contentType(MediaType.of("application/vnd.ms-excel"));
  }

  //----------------ValueSet Version Rule----------------
  @Authorized(Privilege.VS_EDIT)
  @Put(uri = "/{valueSet}/versions/{version}/rule-sets/{id}")
  public HttpResponse<?> updateRuleSet(@PathVariable String valueSet, @PathVariable String version, @PathVariable Long id,
                                    @Body @Valid ValueSetVersionRuleSet ruleSet) {
    ruleSet.setId(id);
    provenanceService.provenanceValueSetVersion("save-rule-set", valueSet, version, () -> {
      valueSetVersionRuleSetService.save(ruleSet, valueSet, version);
    });
    return HttpResponse.created(ruleSet);
  }

  @Authorized(Privilege.VS_EDIT)
  @Post(uri = "/{valueSet}/versions/{version}/rules")
  public HttpResponse<?> createRule(@PathVariable String valueSet, @PathVariable String version, @Body @Valid ValueSetVersionRule rule) {
    rule.setId(null);
    provenanceService.provenanceValueSetVersion("save-rules", valueSet, version, () -> {
      valueSetVersionRuleService.save(rule, valueSet, version);
    });
    return HttpResponse.created(rule);
  }

  @Authorized(Privilege.VS_EDIT)
  @Put(uri = "/{valueSet}/versions/{version}/rules/{id}")
  public HttpResponse<?> updateRule(@PathVariable String valueSet, @PathVariable String version, @PathVariable Long id,
                                    @Body @Valid ValueSetVersionRule rule) {
    rule.setId(id);
    provenanceService.provenanceValueSetVersion("save-rules", valueSet, version, () -> {
      valueSetVersionRuleService.save(rule, valueSet, version);
    });
    return HttpResponse.created(rule);
  }

  @Authorized(Privilege.VS_EDIT)
  @Delete(uri = "/{valueSet}/versions/{version}/rules/{id}")
  public HttpResponse<?> deleteRule(@PathVariable String valueSet, @PathVariable String version, @PathVariable Long id) {
    if (valueSetVersionService.load(valueSet, version).isEmpty()) {
      throw new NotFoundException("ValueSetVersion", version);
    }
    provenanceService.provenanceValueSetVersion("delete-rules", valueSet, version, () -> {
      valueSetVersionRuleService.delete(id);
    });
    return HttpResponse.ok();
  }

  //----------------Provenances----------------

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{valueSet}/provenances")
  public List<Provenance> queryProvenances(@PathVariable String valueSet, @Nullable @QueryValue String version) {
    return provenanceService.find(valueSet, version);
  }

  @Getter
  @Setter
  public static class ValueSetVersionDuplicateRequest {
    private String valueSet;
    private String version;
  }
}
