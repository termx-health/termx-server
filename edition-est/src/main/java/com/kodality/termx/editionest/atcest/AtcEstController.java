package com.kodality.termx.editionest.atcest;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termx.editionest.ApiError;
import com.kodality.termx.editionest.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.util.concurrent.CompletableFuture;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/atc-est")
@RequiredArgsConstructor
public class AtcEstController {
  private final ImportLogger importLogger;
  private final AtcEstService atcSyncService;

  private static final String JOB_TYPE = "ATC-est";

  @Authorized(Privilege.CS_EDIT)
  @Post("/import")
  public JobLogResponse importAtcEst(@NonNull @QueryValue String url, @Body @Valid @NonNull CodeSystemImportConfiguration configuration) {
    JobLogResponse jobLogResponse = importLogger.createJob(configuration.getPublisher(), JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("ATC est import started");
        long start = System.currentTimeMillis();
        atcSyncService.importAtcEst(url, configuration);
        log.info("ATC est import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing ATC est", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing ATC est (OD000)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.EE000.toApiException());
      }
    }));
    return jobLogResponse;
  }
}
