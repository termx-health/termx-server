package com.kodality.termserver.integration.atcest;

import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.ImportLogger;
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
@Controller("/atc-est")
@RequiredArgsConstructor
public class AtcEstController {
  private final ImportLogger importLogger;
  private final AtcEstService atcSyncService;

  private static final String JOB_TYPE = "ATC-est";

  @Post("/import")
  public JobLogResponse importAtcEst(@NonNull @QueryValue String url, @Body ImportConfiguration configuration) {
    String source = configuration.getSource() == null ? AtcEstConfiguration.source : configuration.getSource();
    JobLogResponse jobLogResponse = importLogger.createJob(source, JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("ATC est import started");
        long start = System.currentTimeMillis();
        atcSyncService.importAtcEst(url, configuration);
        log.info("ATC est import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (Exception e) {
        log.error("Error while importing ATC est", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
        throw e;
      }
    }));
    return jobLogResponse;
  }
}
