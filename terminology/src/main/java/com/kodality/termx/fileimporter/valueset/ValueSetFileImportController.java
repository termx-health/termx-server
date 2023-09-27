package com.kodality.termx.fileimporter.valueset;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ApiError;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.fileimporter.valueset.utils.ValueSetFileImportRequest;
import com.kodality.termx.fileimporter.valueset.utils.ValueSetFileImportResponse;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.job.logger.ImportLog;
import com.kodality.termx.sys.job.logger.ImportLogger;
import com.kodality.termx.utils.FileUtil;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.Flowable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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
    String val = URLDecoder.decode(request.getValue(), StandardCharsets.UTF_8);
    ValueSetFileImportRequest req = JsonUtil.fromJson(val, ValueSetFileImportRequest.class);
    byte[] importFile = file != null ? FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()) : null;

    JobLogResponse jobLogResponse = importLogger.createJob(req.getValueSet().getId(), req.getImportClass() == null ? "VS-FILE-IMPORT" : req.getImportClass());
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Value set file import started");
        long start = System.currentTimeMillis();
        ImportLog importLog = new ImportLog();
        ValueSetFileImportResponse resp = req.getLink() != null
            ? fileImporterService.process(req)
            : fileImporterService.process(req, importFile);
        if (CollectionUtils.isNotEmpty(resp.getErrors())) {
          importLog.setErrors(resp.getErrors().stream().map(Issue::formattedMessage).distinct().toList());
        }
        log.info("Value set file import took {} seconds", (System.currentTimeMillis() - start) / 1000);
        importLogger.logImport(jobLogResponse.getJobId(), importLog);
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
