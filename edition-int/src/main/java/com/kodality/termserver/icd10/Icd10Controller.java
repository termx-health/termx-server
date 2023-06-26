package com.kodality.termserver.icd10;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
import com.kodality.termserver.ts.codesystem.CodeSystemImportConfiguration;
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
@Controller("/icd10")
@RequiredArgsConstructor
public class Icd10Controller {
  private final Icd10Service service;
  private final ImportLogger importLogger;

  private static final String JOB_TYPE = "ICD-10";

  @Authorized(Privilege.CS_EDIT)
  @Post("/import")
  public JobLogResponse importIcd10(@NonNull @QueryValue String url, @Body @Valid @NonNull CodeSystemImportConfiguration configuration) {
    JobLogResponse jobLogResponse = importLogger.createJob(configuration.getPublisher(), JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("ICD-10 import started");
        long start = System.currentTimeMillis();
        service.importIcd10(url, configuration);
        log.info("ICD-10 import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing ICD-10", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing ICD-10 (EI000)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.EI000.toApiException());
      }
    }));
    return jobLogResponse;
  }
}
