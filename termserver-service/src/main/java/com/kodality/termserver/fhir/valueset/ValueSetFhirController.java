package com.kodality.termserver.fhir.valueset;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/fhir/ValueSet")
@RequiredArgsConstructor
public class ValueSetFhirController {
  private final ImportLogger importLogger;
  private final ValueSetFhirService service;
  private final ValueSetFhirImportService importService;

  private static final String JOB_TYPE = "FHIR-VS";

  @Authorized("*.ValueSet.view")
  @Get("/{valueSetVersionId}")
  public HttpResponse<?> getValueSet(Long valueSetVersionId) {
    ValueSet valueSet = service.get(valueSetVersionId);
    if (valueSet == null) {
      throw new NotFoundException("ValueSet not found");
    }
    return HttpResponse.ok(valueSet);
  }

  @Authorized("*.ValueSet.view")
  @Get("/$expand{?params*}")
  public HttpResponse<?> expand(Map<String, List<String>> params) {
    OperationOutcome outcome = new OperationOutcome();
    ValueSet valueSet = service.expand(params, outcome);
    if (valueSet == null) {
      return HttpResponse.badRequest(outcome);
    }
    return HttpResponse.ok(valueSet);
  }

  @Authorized("*.ValueSet.view")
  @Get("/$validate-code{?params*}")
  public HttpResponse<?> validateCode(Map<String, List<String>> params) {
    OperationOutcome outcome = new OperationOutcome();
    Parameters parameters = service.validateCode(params, outcome);
    if (parameters == null) {
      return HttpResponse.badRequest(outcome);
    }
    return HttpResponse.ok(parameters);
  }

  @Authorized("*.ValueSet.edit")
  @Post("/$sync")
  public HttpResponse<?> importFhirValueSet(@Body Parameters parameters) {
    JobLogResponse jobLogResponse = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        List<String> successes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        log.info("Fhir value set import started");
        long start = System.currentTimeMillis();
        importService.importValueSets(parameters, successes, warnings);
        log.info("Fhir value set import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId(), successes, warnings);
      } catch (ApiClientException e) {
        log.error("Error while importing fhir value set", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing fhir value set (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    Parameters resp =  new Parameters().setParameter(List.of(new ParametersParameter().setName("jobId").setValueDecimal(BigDecimal.valueOf(jobLogResponse.getJobId()))));
    return HttpResponse.ok(resp);
  }
}
