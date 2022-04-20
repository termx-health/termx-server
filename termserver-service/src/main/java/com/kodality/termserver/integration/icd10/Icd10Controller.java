package com.kodality.termserver.integration.icd10;

import com.kodality.termserver.integration.common.ImportConfiguration;
import com.kodality.termserver.integration.common.ImportLogger;
import com.kodality.termserver.job.JobLogResponse;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/icd10")
@RequiredArgsConstructor
public class Icd10Controller {
  private final Icd10Service service;
  private final ImportLogger importLogger;

  private static final String JOB_TYPE = "ICD-10";

  @Post("/import")
  public JobLogResponse importIcd10(@NonNull @QueryValue String url, @Body ImportConfiguration configuration) {
    String source = configuration.getSource() == null ? Icd10Configuration.source : configuration.getSource();
    JobLogResponse jobLogResponse = importLogger.createJob(source, JOB_TYPE);
    CompletableFuture.runAsync(() -> {
      try {
        log.info("ICD-10 import started");
        long start = System.currentTimeMillis();
        service.importIcd10(url, configuration);
        log.info("ICD-10 import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (Exception e) {
        log.error("Error while importing ICD-10", e);
        throw e;
      }
    });
    return jobLogResponse;
  }
}
