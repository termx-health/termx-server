package com.kodality.termserver.fhir.valueset;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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


  @Get("/{valueSetVersionId}")
  public HttpResponse<?> getValueSet(Long valueSetVersionId) {
    ValueSet valueSet = service.get(valueSetVersionId);
    if (valueSet == null) {
      throw new NotFoundException("ValueSet not found");
    }
    return HttpResponse.ok(valueSet);
  }

  @Post("/$sync")
  public HttpResponse<?> importFhirValueSet(@Body Parameters parameters) {
    JobLogResponse jobLogResponse = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(() -> {
      try {
        List<String> successes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        log.info("Fhir value set import started");
        long start = System.currentTimeMillis();
        importService.importValueSets(parameters, successes, warnings);
        log.info("Fhir value set import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId(), successes, warnings);
      } catch (Exception e) {
        log.error("Error while importing fhir value set", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
        throw e;
      }
    });
    Parameters resp =  new Parameters().setParameter(List.of(new Parameter().setName("jobId").setValueDecimal(BigDecimal.valueOf(jobLogResponse.getJobId()))));
    return HttpResponse.ok(resp);
  }
}
