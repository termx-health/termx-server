package com.kodality.termserver.icd10est;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.job.logger.ImportLogger;
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
@Controller("/icd10-est")
@RequiredArgsConstructor
public class Icd10EstController {
  private final Icd10EstService service;
  private final ImportLogger importLogger;

  private static final String JOB_TYPE = "RHK-10";

  @Authorized("*.CodeSystem.edit")
  @Post("/import")
  public JobLogResponse importIcd10Est(@NonNull @QueryValue String url, @Body @Valid @NonNull CodeSystemImportConfiguration configuration) {
    JobLogResponse jobLogResponse = importLogger.createJob(configuration.getSource(), JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("ICD-10 est import started");
        long start = System.currentTimeMillis();
        service.importIcd10Est(url, configuration);
        log.info("ICD-10 est import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing ICD-10 est", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing ICD-10 est (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.EE000.toApiException());
      }
    }));
    return jobLogResponse;
  }
}
