package com.kodality.termserver.integration.fhir;

import com.kodality.termserver.integration.common.ImportLogger;
import com.kodality.termserver.integration.fhir.codesystem.FhirCodeSystemImportService;
import com.kodality.termserver.integration.fhir.valueset.FhirValueSetImportService;
import com.kodality.termserver.job.JobLogResponse;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/fhir")
@RequiredArgsConstructor
public class FhirController {
  private final ImportLogger importLogger;
  private final FhirValueSetImportService valueSetImportService;
  private final FhirCodeSystemImportService codeSystemImportService;

  private static final String JOB_TYPE = "FHIR";

  @Post("/code-system-import")
  public JobLogResponse importFhirCodeSystem(@NonNull @QueryValue String url) {
    JobLogResponse jobLogResponse = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(() -> {
      try {
        log.info("Fhir code system import started");
        long start = System.currentTimeMillis();
        codeSystemImportService.importCodeSystem(url);
        log.info("Fhir code system import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (Exception e) {
        log.error("Error while importing fhir code system", e);
        throw e;
      }
    });
    return jobLogResponse;
  }

  @Post("/value-set-import")
  public JobLogResponse importFhirValueSet(@NonNull @QueryValue String url) {
    JobLogResponse jobLogResponse = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(() -> {
      try {
        log.info("Fhir value set import started");
        long start = System.currentTimeMillis();
        valueSetImportService.importValueSet(url);
        log.info("Fhir value set import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (Exception e) {
        log.error("Error while importing fhir value set", e);
        throw e;
      }
    });
    return jobLogResponse;
  }
}
