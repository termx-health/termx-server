package com.kodality.termx.terminology.fileimporter.codesystem;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.utils.FileUtil;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResponse;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.logger.ImportLog;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.rxjava3.core.Flowable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/file-importer/code-system")
@RequiredArgsConstructor
public class CodeSystemFileImportController {
  private final CodeSystemFileImportService fileImporterService;
  private final ImportLogger importLogger;

  @Authorized(Privilege.CS_EDIT)
  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(@Nullable Publisher<CompletedFileUpload> file, @Part("request") String request) {
    String val = URLDecoder.decode(request, StandardCharsets.UTF_8);
    CodeSystemFileImportRequest req = JsonUtil.fromJson(val, CodeSystemFileImportRequest.class);

    CompletedFileUpload fileUpload = file != null ? Flowable.fromPublisher(file).firstElement().blockingGet() : null;
    byte[] importFile = fileUpload != null ? FileUtil.readBytes(fileUpload) : null;

    JobLogResponse jobLogResponse = importLogger.createJob(req.getCodeSystem().getId(), req.getImportClass() == null ? "CS-FILE-IMPORT" : req.getImportClass());
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Code system file import started");
        long start = System.currentTimeMillis();
        CodeSystemFileImportResponse resp = req.getLink() != null
            ? fileImporterService.process(req)
            : fileImporterService.process(req, importFile);

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
        log.error("Error while importing code system file " +  req.getCodeSystem().getId(), e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing code system file " + req.getCodeSystem().getId(), e);
        importLogger.logImport(jobLogResponse.getJobId(), null, null, List.of(ExceptionUtils.getStackTrace(e)));
      }
    }));
    return jobLogResponse;
  }
}
