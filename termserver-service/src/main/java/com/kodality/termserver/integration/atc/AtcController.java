package com.kodality.termserver.integration.atc;

import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.job.JobLogResponse;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import java.util.concurrent.CompletableFuture;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/atc")
@RequiredArgsConstructor
public class AtcController {

  private final AtcService atcService;
  private final ImportLogger importLogger;

  private static final String JOB_TYPE = "ATC";

  @Post("/import")
  public JobLogResponse importAtc(@Body @Valid @NonNull ImportConfiguration configuration) {
    JobLogResponse jobLogResponse = importLogger.createJob(configuration.getSource(), JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("ATC import started");
        long start = System.currentTimeMillis();
        atcService.importAtc(configuration);
        log.info("ATC import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (Exception e) {
        log.error("Error while importing ATC", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
        throw e;
      }
    }));
    return jobLogResponse;
  }
}
