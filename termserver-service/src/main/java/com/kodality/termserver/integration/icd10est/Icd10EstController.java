package com.kodality.termserver.integration.icd10est;

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
@Controller("/icd10-est")
@RequiredArgsConstructor
public class Icd10EstController {
  private final Icd10EstService service;
  private final ImportLogger importLogger;

  private static final String JOB_TYPE = "RHK-10";

  @Post("/import")
  public JobLogResponse importIcd10Est(@NonNull @QueryValue String url, @Body ImportConfiguration configuration) {
    String source = configuration.getSource() == null ? Icd10EstConfiguration.source : configuration.getSource();
    JobLogResponse jobLogResponse = importLogger.createJob(source, JOB_TYPE);
    CompletableFuture.runAsync(() -> {
      try {
        log.info("ICD-10 est import started");
        long start = System.currentTimeMillis();
        service.importIcd10Est(url, configuration);
        log.info("ICD-10 est import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (Exception e) {
        log.error("Error while importing ICD-10 est", e);
        throw e;
      }
    });
    return jobLogResponse;
  }
}
