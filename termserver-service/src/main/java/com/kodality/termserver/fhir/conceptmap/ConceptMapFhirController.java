package com.kodality.termserver.fhir.conceptmap;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import io.micronaut.core.util.CollectionUtils;
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
@Controller("/fhir/ConceptMap")
@RequiredArgsConstructor
public class ConceptMapFhirController {
  private final ImportLogger importLogger;
  private final ConceptMapFhirService service;
  private final ConceptMapFhirImportService importService;

  private static final String JOB_TYPE = "FHIR-MS";

  @Get("/$translate{?params*}")
  public HttpResponse<?> translate(Map<String, List<String>> params) {
    Parameters parameters = service.translate(params);
    if (CollectionUtils.isEmpty(parameters.getParameter())) {
      return HttpResponse.badRequest(service.error(params));
    }
    return HttpResponse.ok(parameters);
  }

  @Post("/$closure")
  public HttpResponse<?> closure(@Body Parameters params) {
    OperationOutcome outcome = new OperationOutcome();
    ConceptMap conceptMap = service.closure(params, outcome);
    if (CollectionUtils.isNotEmpty(outcome.getIssue())) {
      return HttpResponse.badRequest(outcome);
    }
    return HttpResponse.ok(conceptMap);
  }

  @Post("/$sync")
  public HttpResponse<?> importFhirMapSet(@Body Parameters parameters) {
    JobLogResponse jobLogResponse = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        List<String> successes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        log.info("Fhir map set import started");
        long start = System.currentTimeMillis();
        importService.importMapSets(parameters, successes, warnings);
        log.info("Fhir map set import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId(),successes, warnings);
      } catch (ApiClientException e) {
        log.error("Error while importing fhir map set", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing fhir map set (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    Parameters resp =  new Parameters().setParameter(List.of(new Parameter().setName("jobId").setValueDecimal(BigDecimal.valueOf(jobLogResponse.getJobId()))));
    return HttpResponse.ok(resp);
  }
}
