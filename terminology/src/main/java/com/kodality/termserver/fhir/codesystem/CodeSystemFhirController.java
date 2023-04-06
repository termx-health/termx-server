package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.job.logger.ImportLogger;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Produces({"application/json", "application/fhir+json"})
@Consumes({"application/json", "application/fhir+json"})
@Slf4j
@Controller("/fhir/CodeSystem")
@RequiredArgsConstructor
public class CodeSystemFhirController {
  private final ImportLogger importLogger;
  private final CodeSystemFhirService service;
  private final CodeSystemFhirImportService importService;

  private static final String JOB_TYPE = "FHIR-CS";

  @Get("/{codeSystemId}{?params*}")
  public HttpResponse<?> getCodeSystem(String codeSystemId, Map<String, List<String>> params) {
    CodeSystem codeSystem = service.get(codeSystemId, params);
    if (codeSystem == null) {
      throw new NotFoundException("CodeSystem not found");
    }
    return HttpResponse.ok(codeSystem);
  }

  @Get("{?params*}")
  public HttpResponse<?> searchCodeSystems(Map<String, List<String>> params) {
    Bundle bundle = service.search(params);
    return HttpResponse.ok(bundle);
  }

  @Authorized("*.CodeSystem.edit")
  @Put("{?params*}")
  public HttpResponse<?> saveCodeSystem(@QueryValue Optional<String> url, @QueryValue Optional<String> version, @Body CodeSystem codeSystem) {
    service.save(url, version, codeSystem);
    return HttpResponse.ok(codeSystem);
  }

  @Get("/$lookup{?params*}")
  public HttpResponse<?> lookup(Map<String, List<String>> params) {
    Parameters resp = service.lookup(params);
    return HttpResponse.ok(FhirMapper.toJson(resp));
  }

  @Get("/$validate-code{?params*}")
  public HttpResponse<?> validateCode(Map<String, List<String>> params) {
    Parameters resp = service.validateCode(getFirst(params, "code"), getFirst(params, "url"), getFirst(params, "version"), getFirst(params, "display"));
    return HttpResponse.ok(FhirMapper.toJson(resp));
  }

  public String getFirst(Map<String, List<String>> params, String key) {
    return params.containsKey(key) ? params.get(key).stream().findFirst().orElse(null) : null;
  }

  @Post("/$validate-code")
  public HttpResponse<?> validateCode(Parameters parameters) {
    String url = parameters.findParameter("url").map(ParametersParameter::getValueString).orElse(null);
    String version = parameters.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
    String code = parameters.findParameter("code").map(ParametersParameter::getValueString).orElse(null);
    String display = parameters.findParameter("display").map(ParametersParameter::getValueString).orElse(null);
    Parameters resp = service.validateCode(code, url, version, display);
    return HttpResponse.ok(FhirMapper.toJson(resp));
  }

  @Get("/$subsumes{?params*}")
  public HttpResponse<?> subsumes(Map<String, List<String>> params) {
    OperationOutcome outcome = new OperationOutcome();
    Parameters parameters = service.subsumes(params, outcome);
    if (CollectionUtils.isNotEmpty(outcome.getIssue())) {
      return HttpResponse.badRequest(outcome);
    }
    return HttpResponse.ok(parameters);
  }

  @Post("/$subsumes")
  public HttpResponse<?> subsumes(@Body Parameters params) {
    OperationOutcome outcome = new OperationOutcome();
    Parameters parameters = service.subsumes(params, outcome);
    if (CollectionUtils.isNotEmpty(outcome.getIssue())) {
      return HttpResponse.badRequest(outcome);
    }
    return HttpResponse.ok(parameters);
  }

  @Post("/$find-matches")
  public HttpResponse<?> findMatches(@Body Parameters params) {
    OperationOutcome outcome = new OperationOutcome();
    Parameters parameters = service.findMatches(params, outcome);
    if (CollectionUtils.isNotEmpty(outcome.getIssue())) {
      return HttpResponse.badRequest(outcome);
    }
    return HttpResponse.ok(parameters);
  }

  @Authorized("*.CodeSystem.edit")
  @Post("/$sync")
  public HttpResponse<?> importFhirCodeSystem(@Body Parameters parameters) {
    JobLogResponse jobLogResponse = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        List<String> successes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        log.info("Fhir code system import started");
        long start = System.currentTimeMillis();
        importService.importCodeSystems(parameters, successes, warnings);
        log.info("Fhir code system import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId(), successes, warnings);
      } catch (ApiClientException e) {
        log.error("Error while importing fhir code system", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing fhir code system (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    Parameters resp = new Parameters().setParameter(List.of(new ParametersParameter().setName("jobId").setValueDecimal(BigDecimal.valueOf(jobLogResponse.getJobId()))));
    return HttpResponse.ok(resp);
  }
}
