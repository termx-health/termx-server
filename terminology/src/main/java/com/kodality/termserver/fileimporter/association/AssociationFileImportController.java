package com.kodality.termserver.fileimporter.association;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fileimporter.FileImporterUtils;
import com.kodality.termserver.fileimporter.association.utils.AssociationFileImportRequest;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.Flowable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/file-importer/association")
@RequiredArgsConstructor
public class AssociationFileImportController {
  private final AssociationFileImportService fileImporterService;
  private final ImportLogger importLogger;

  @Authorized("*.CodeSystem.edit")
  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    AssociationFileImportRequest req = JsonUtil.fromJson(request.getValue(), AssociationFileImportRequest.class);
    byte[] importFile = FileImporterUtils.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet());

    JobLogResponse jobLogResponse = importLogger.createJob("ASSOCIATION-FILE-IMPORT");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Association file import started");
        long start = System.currentTimeMillis();
        fileImporterService.process(req, importFile);
        log.info("Association file import took {} seconds", (System.currentTimeMillis() - start) / 1000);
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiException e) {
        log.error("Error while importing Association file", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (ConstraintViolationException e) {
        log.error("Constraint error while importing Association file", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE719.toApiException(Map.of("error", e.getMessage())));
      } catch (Exception e) {
        log.error("Error while importing Association file (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));

    return jobLogResponse;
  }
}
