package com.kodality.termserver.loinc;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
import com.kodality.termserver.loinc.utils.LoincImportRequest;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.Flowable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/loinc")
@RequiredArgsConstructor
public class LoincController {
  private final LoincService loincService;
  private final ImportLogger importLogger;

  @Authorized(Privilege.CS_EDIT)
  @Post(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(
      @Nullable Publisher<CompletedFileUpload> partsFile,
      @Nullable Publisher<CompletedFileUpload> terminologyFile,
      @Nullable Publisher<CompletedFileUpload> supplementaryPropertiesFile,
      @Nullable Publisher<CompletedFileUpload> panelsFile,
      @Nullable Publisher<CompletedFileUpload> answerListFile,
      @Nullable Publisher<CompletedFileUpload> answerListLinkFile,
      @Nullable Publisher<CompletedFileUpload> translationsFile,
      @Nullable Publisher<CompletedFileUpload> orderObservationFile,
      @Part("request") MemoryAttribute request) {
    LoincImportRequest req = JsonUtil.fromJson(request.getValue(), LoincImportRequest.class);
    List<Pair<String, byte[]>> files = List.of(
        Pair.of("parts", partsFile != null ? readBytes(Flowable.fromPublisher(partsFile).firstOrError().blockingGet()) : null),
        Pair.of("terminology", terminologyFile != null ? readBytes(Flowable.fromPublisher(terminologyFile).firstOrError().blockingGet()) : null),
        Pair.of("supplementary-properties",
            supplementaryPropertiesFile != null ? readBytes(Flowable.fromPublisher(supplementaryPropertiesFile).firstOrError().blockingGet()) : null),
        Pair.of("panels", panelsFile != null ? readBytes(Flowable.fromPublisher(panelsFile).firstOrError().blockingGet()) : null),
        Pair.of("answer-list", answerListFile != null ? readBytes(Flowable.fromPublisher(answerListFile).firstOrError().blockingGet()) : null),
        Pair.of("answer-list-link", answerListLinkFile != null ? readBytes(Flowable.fromPublisher(answerListLinkFile).firstOrError().blockingGet()) : null),
        Pair.of("translations", translationsFile != null ? readBytes(Flowable.fromPublisher(translationsFile).firstOrError().blockingGet()) : null),
        Pair.of("order-observation", orderObservationFile != null ? readBytes(Flowable.fromPublisher(orderObservationFile).firstOrError().blockingGet()) : null));

    JobLogResponse jobLogResponse = importLogger.createJob("LOINC-IMPORT");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("LOINC import started");
        long start = System.currentTimeMillis();
        loincService.importLoinc(req, files);
        log.info("LOINC import took {} seconds", (System.currentTimeMillis() - start) / 1000);
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing LOINC", e);
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
