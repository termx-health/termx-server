package com.kodality.termserver.integration.fileimporter.mapset;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.integration.fileimporter.mapset.utils.MapSetFileImportRequest;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.job.logger.ImportLogger;
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
@Controller("/file-importer/map-set")
@RequiredArgsConstructor
public class MapSetFileImportController {
  private final ImportLogger importLogger;
  private final MapSetFileImportService importService;

  @Authorized("*.MapSet.edit")
  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    JobLogResponse jobLogResponse = importLogger.createJob( "MS-FILE-IMPORT");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Map set file import started");
        long start = System.currentTimeMillis();
        CompletedFileUpload importFile = Flowable.fromPublisher(file).firstOrError().blockingGet();
        MapSetFileImportRequest req = JsonUtil.fromJson(request.getValue(), MapSetFileImportRequest.class);
        importService.process(req, importFile.getBytes());
        log.info("Map set file import took {} seconds", (System.currentTimeMillis() - start) / 1000);
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing map set file", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing map set file (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    return jobLogResponse;
  }
}
