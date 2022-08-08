package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.fhir.FhirMeasurementUnitConvertor;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/fhir/CodeSystem")
@RequiredArgsConstructor
public class CodeSystemFhirController {
  private final ImportLogger importLogger;
  private final CodeSystemFhirService service;
  private final CodeSystemFhirImportService importService;
  private final FhirMeasurementUnitConvertor fhirMeasurementUnitConvertor;

  private static final String JOB_TYPE = "FHIR-CS";

  @Get("/{codeSystemVersionId}")
  public HttpResponse<?> getCodeSystem(Long codeSystemVersionId) {
    CodeSystem codeSystem = service.get(codeSystemVersionId);
    if (codeSystem == null) {
      throw new NotFoundException("CodeSystem not found");
    }
    return HttpResponse.ok(codeSystem);
  }

  @Get("/$lookup{?params*}")
  public HttpResponse<?> lookup(Map<String, List<String>> params) {
    Parameters parameters = service.lookup(params);
    if (CollectionUtils.isEmpty(parameters.getParameter())) {
      return HttpResponse.badRequest(service.error(params));
    }
    return HttpResponse.ok(parameters);
  }

  @Get("/$validate-code{?params*}")
  public HttpResponse<?> validateCode(Map<String, List<String>> params) {
    Parameters parameters = service.validateCode(params);
    if (CollectionUtils.isEmpty(parameters.getParameter())) {
      return HttpResponse.badRequest(service.error(params));
    }
    return HttpResponse.ok(parameters);
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
      } catch (Exception e) {
        log.error("Error while importing fhir code system", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
        throw e;
      }
    }));
    Parameters resp = new Parameters().setParameter(List.of(new Parameter().setName("jobId").setValueDecimal(BigDecimal.valueOf(jobLogResponse.getJobId()))));
    return HttpResponse.ok(resp);
  }

  @Get("/ucum/$translate")
  public HttpResponse<Parameters> translate(@QueryValue @NotNull BigDecimal value, @QueryValue @NotNull String sourceUnit, @QueryValue @NotNull String targetUnit) {
    return HttpResponse.ok(fhirMeasurementUnitConvertor.convert(value, sourceUnit, targetUnit));
  }
}
