package com.kodality.termserver.integration.orphanet;

import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.integration.icd10.Icd10Configuration;
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
@Controller("/orphanet")
@RequiredArgsConstructor
public class OrphanetController {

  private final OrphanetService service;
  private final ImportLogger importLogger;

  private static final String JOB_TYPE = "Orphanet";

  @Post("/import")
  public JobLogResponse importIcd10(@NonNull @QueryValue String url, @Body ImportConfiguration configuration) {
    String source = configuration.getSource() == null ? Icd10Configuration.source : configuration.getSource();
    JobLogResponse jobLogResponse = importLogger.createJob(source, JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Orphanet import started");
        long start = System.currentTimeMillis();
        service.importOrpha(url, configuration);
        log.info("Orphanet import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (Exception e) {
        log.error("Error while importing Orphanet", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
        throw e;
      }
    }));
    return jobLogResponse;
  }
}
