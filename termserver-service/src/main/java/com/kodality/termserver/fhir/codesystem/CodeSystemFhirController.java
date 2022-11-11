package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.fhir.FhirMeasurementUnitConvertor;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.ParametersParameter;
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

  @Authorized("*.CodeSystem.view")
  @Get("/{codeSystemVersionId}")
  public HttpResponse<?> getCodeSystem(Long codeSystemVersionId) {
    CodeSystem codeSystem = service.get(codeSystemVersionId);
    if (codeSystem == null) {
      throw new NotFoundException("CodeSystem not found");
    }
    return HttpResponse.ok(codeSystem);
  }

  @Authorized("*.CodeSystem.view")
  @Get("/$lookup{?params*}")
  public HttpResponse<?> lookup(Map<String, List<String>> params) {
    Parameters parameters = service.lookup(params);
    if (CollectionUtils.isEmpty(parameters.getParameter())) {
      return HttpResponse.badRequest(service.error(params));
    }
    return HttpResponse.ok(parameters);
  }

  @Authorized("*.CodeSystem.view")
  @Get("/$validate-code{?params*}")
  public HttpResponse<?> validateCode(Map<String, List<String>> params) {
    Parameters parameters = service.validateCode(params);
    if (CollectionUtils.isEmpty(parameters.getParameter())) {
      return HttpResponse.badRequest(service.error(params));
    }
    return HttpResponse.ok(parameters);
  }

  @Authorized("*.CodeSystem.view")
  @Get("/$subsumes{?params*}")
  public HttpResponse<?> subsumes(Map<String, List<String>> params) {
    OperationOutcome outcome = new OperationOutcome();
    Parameters parameters = service.subsumes(params, outcome);
    if (CollectionUtils.isNotEmpty(outcome.getIssue())) {
      return HttpResponse.badRequest(outcome);
    }
    return HttpResponse.ok(parameters);
  }

  @Authorized("*.CodeSystem.view")
  @Post("/$subsumes")
  public HttpResponse<?> subsumes(@Body Parameters params) {
    OperationOutcome outcome = new OperationOutcome();
    Parameters parameters = service.subsumes(params, outcome);
    if (CollectionUtils.isNotEmpty(outcome.getIssue())) {
      return HttpResponse.badRequest(outcome);
    }
    return HttpResponse.ok(parameters);
  }

  @Authorized("*.CodeSystem.view")
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

  @Authorized("*.MeasurementUnit.view")
  @Get("/ucum/$translate")
  public HttpResponse<Parameters> translate(@QueryValue @NotNull BigDecimal value, @QueryValue @NotNull String sourceUnit, @QueryValue @NotNull String targetUnit) {
    return HttpResponse.ok(fhirMeasurementUnitConvertor.convert(value, sourceUnit, targetUnit));
  }
}
