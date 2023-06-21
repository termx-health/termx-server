package com.kodality.termserver.fileimporter.valueset;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fileimporter.FileImporterUtils;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
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
@Controller("/file-importer/value-set")
@RequiredArgsConstructor
public class ValueSetFileImportController {
  private final ValueSetFileImportService fileImporterService;
  private final ImportLogger importLogger;

  @Authorized(Privilege.CS_EDIT)
  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(@Nullable Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    ValueSetFileImportRequest req = JsonUtil.fromJson(request.getValue(), ValueSetFileImportRequest.class);
    byte[] importFile = file != null ? FileImporterUtils.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()) : null;

    JobLogResponse jobLogResponse = importLogger.createJob("VS-FILE-IMPORT");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Value set file import started");
        long start = System.currentTimeMillis();
        fileImporterService.process(req, importFile);
        log.info("Value set file import took {} seconds", (System.currentTimeMillis() - start) / 1000);
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiException e) {
        log.error("Error while importing value set file", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing value set file (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    return jobLogResponse;
  }
}
