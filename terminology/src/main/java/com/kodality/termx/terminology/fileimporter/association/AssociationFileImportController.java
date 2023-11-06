package com.kodality.termx.terminology.fileimporter.association;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.utils.FileUtil;
import com.kodality.termx.terminology.fileimporter.association.utils.AssociationFileImportRequest;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.rxjava3.core.Flowable;
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

  @Authorized(Privilege.CS_EDIT)
  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    AssociationFileImportRequest req = JsonUtil.fromJson(request.getValue(), AssociationFileImportRequest.class);
    byte[] importFile = FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet());

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

  @Authorized(Privilege.CS_EDIT)
  @Get(value = "/csv-template", produces = "application/csv")
  public HttpResponse<?> getTemplate() {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(fileImporterService.getTemplate());
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template.csv")
        .contentType(MediaType.of("application/csv"));
  }
}
