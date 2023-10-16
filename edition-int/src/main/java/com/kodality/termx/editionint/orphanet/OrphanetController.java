package com.kodality.termx.editionint.orphanet;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.editionint.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termx.core.utils.FileUtil;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.Flowable;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/orphanet")
@RequiredArgsConstructor
public class OrphanetController {

  private final OrphanetService service;
  private final ImportLogger importLogger;

  private static final String JOB_TYPE = "Orphanet";

  @Authorized(Privilege.CS_EDIT)
  @Post(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse importIcd10(@Nullable Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    CodeSystemImportConfiguration configuration = JsonUtil.fromJson(request.getValue(), CodeSystemImportConfiguration.class);
    byte[] importFile = file != null ? FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()) : null;

    JobLogResponse jobLogResponse = importLogger.createJob(configuration.getPublisher(), JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Orphanet import started");
        long start = System.currentTimeMillis();
        if (file != null) {
          service.importOrpha(importFile, configuration);
        } else {
          service.importOrpha(configuration);
        }
        log.info("Orphanet import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing Orphanet", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing Orphanet", e);
        importLogger.logImport(jobLogResponse.getJobId(), new ApiException(500, Issue.error(e.getMessage())));
      }
    }));
    return jobLogResponse;
  }
}
