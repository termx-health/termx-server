package com.kodality.termserver.integration.fileimporter.codesystem;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileAnalysisRequest;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingRequest;
import com.kodality.termserver.job.JobLogResponse;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.Flowable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/file-importer/code-system")
@RequiredArgsConstructor
public class CodeSystemFileImportController {
  private final CodeSystemFileImportService fileImporterService;
  private final ImportLogger importLogger;

  @Authorized("*.CodeSystem.view")
  @Post(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA)
  public HttpResponse<?> analyze(@Nullable Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    FileAnalysisRequest req = JsonUtil.fromJson(request.getValue(), FileAnalysisRequest.class);
    byte[] importFile = file != null ? readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()) : null;

    if (file != null) {
      return HttpResponse.ok(fileImporterService.analyze(req, importFile));
    }
    return HttpResponse.ok(fileImporterService.analyze(req));
  }

  @Authorized("*.CodeSystem.edit")
  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(@Nullable Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    FileProcessingRequest req = JsonUtil.fromJson(request.getValue(), FileProcessingRequest.class);
    byte[] importFile = file != null ? readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()) : null;

    JobLogResponse jobLogResponse = importLogger.createJob("CS-FILE-IMPORT");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Code system file import started");
        long start = System.currentTimeMillis();
        if (file != null) {
          fileImporterService.process(req, importFile);
        } else {
          fileImporterService.process(req);
        }
        log.info("Code system file import took {} seconds", (System.currentTimeMillis() - start) / 1000);
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing code system file", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing code system file (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    return jobLogResponse;
  }


  private byte[] readBytes(CompletedFileUpload file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
