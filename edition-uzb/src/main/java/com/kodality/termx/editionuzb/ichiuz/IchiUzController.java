package com.kodality.termx.editionuzb.ichiuz;


import com.kodality.commons.exception.ApiClientException;
import com.kodality.termx.editionuzb.ApiError;
import com.kodality.termx.editionuzb.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.util.concurrent.CompletableFuture;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/ichi-uz")
@RequiredArgsConstructor
public class IchiUzController {
  private final IchiUzService service;
  private final ImportLogger importLogger;

  private static final String JOB_TYPE = "ICHI";

  @Authorized(Privilege.CS_EDIT)
  @Post("/import")
  public JobLogResponse importIchiUz(@NonNull @QueryValue String url, @Body @Valid @NonNull CodeSystemImportConfiguration configuration) {
    JobLogResponse jobLogResponse = importLogger.createJob(configuration.getPublisher(), JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("ICHI uz import started");
        long start = System.currentTimeMillis();
        service.importIchiUz(url, configuration);
        log.info("ICHI uz import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing ICHI uz", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing ICHI uz (EU000)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.EU000.toApiException());
      }
    }));
    return jobLogResponse;
  }
}
