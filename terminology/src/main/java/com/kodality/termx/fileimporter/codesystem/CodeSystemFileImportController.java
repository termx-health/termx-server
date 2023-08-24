package com.kodality.termx.fileimporter.codesystem;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ApiError;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.utils.FileUtil;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportRequest;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportResponse;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.job.logger.ImportLog;
import com.kodality.termx.sys.job.logger.ImportLogger;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.Flowable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/file-importer/code-system")
@RequiredArgsConstructor
public class CodeSystemFileImportController {
  private final CodeSystemFileImportService fileImporterService;
  private final ImportLogger importLogger;


  @Authorized(Privilege.CS_EDIT)
  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(@Nullable Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    CodeSystemFileImportRequest req = JsonUtil.fromJson(request.getValue(), CodeSystemFileImportRequest.class);
    byte[] importFile = file != null ? FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()) : null;

    JobLogResponse jobLogResponse = importLogger.createJob("CS-FILE-IMPORT");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Code system file import started");
        long start = System.currentTimeMillis();
        CodeSystemFileImportResponse resp = file != null
            ? fileImporterService.process(req, importFile)
            : fileImporterService.process(req);

        log.info("Code system file import took {} seconds", (System.currentTimeMillis() - start) / 1000);
        ImportLog importLog = new ImportLog();
        if (StringUtils.isNotBlank(resp.getDiff())) {
          importLog.setWarnings(List.of(resp.getDiff()));
        }
        if (CollectionUtils.isNotEmpty(resp.getErrors())) {
          importLog.setErrors(resp.getErrors().stream().map(Issue::formattedMessage).distinct().toList());
        }
        importLogger.logImport(jobLogResponse.getJobId(), importLog);
      } catch (ApiException e) {
        log.error("Error while importing code system file", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing code system file", e);
        importLogger.logImport(jobLogResponse.getJobId(), null, null, List.of(e.getMessage()));
      }
    }));
    return jobLogResponse;
  }
}
