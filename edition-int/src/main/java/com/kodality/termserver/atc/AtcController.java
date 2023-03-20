package com.kodality.termserver.atc;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.CommonSessionProvider;
import com.kodality.termserver.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.job.logger.ImportLogger;
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
  private final CommonSessionProvider commonSessionProvider;

  private static final String JOB_TYPE = "ATC";

  @Authorized("*.CodeSystem.edit")
  @Post("/import")
  public JobLogResponse importAtc(@Body @Valid @NonNull CodeSystemImportConfiguration configuration) {
    JobLogResponse jobLogResponse = importLogger.createJob(configuration.getSource(), JOB_TYPE);
    CompletableFuture.runAsync(commonSessionProvider.wrap(() -> {
      try {
        log.info("ATC import started");
        long start = System.currentTimeMillis();
        atcService.importAtc(configuration);
        log.info("ATC import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing ATC", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing ATC (EI000)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.EI000.toApiException());
      }
    }));
    return jobLogResponse;
  }
}
