package com.kodality.termserver.fhir.codesystem;

import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/fhir/CodeSystem")
@RequiredArgsConstructor
public class CodeSystemFhirController {
  private final ImportLogger importLogger;
  private final CodeSystemFhirService service;
  private final CodeSystemFhirImportService importService;

  private static final String JOB_TYPE = "FHIR-CS";


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

  @Post("/$sync")
  public HttpResponse<?> importFhirCodeSystem(@Body Parameters parameters) {
    JobLogResponse jobLogResponse = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(() -> {
      try {
        log.info("Fhir code system import started");
        long start = System.currentTimeMillis();
        importService.importCodeSystem(parameters);
        log.info("Fhir code system import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (Exception e) {
        log.error("Error while importing fhir code system", e);
        throw e;
      }
    });
    Parameters resp =  new Parameters().setParameter(List.of(new Parameter().setName("jobId").setValueDecimal(BigDecimal.valueOf(jobLogResponse.getJobId()))));
    return HttpResponse.ok(resp);
  }
}
