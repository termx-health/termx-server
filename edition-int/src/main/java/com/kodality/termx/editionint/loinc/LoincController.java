package com.kodality.termx.editionint.loinc;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.core.utils.FileUtil;
import com.kodality.termx.editionint.Privilege;
import com.kodality.termx.editionint.loinc.utils.LoincImportRequest;
import com.kodality.termx.sys.job.JobLogResponse;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/loinc")
@RequiredArgsConstructor
public class LoincController {
  public static final String JOB_TYPE = "loinc-import";

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
      @Part("request") String request) {
    LoincImportRequest req = JsonUtil.fromJson(request, LoincImportRequest.class);
    List<Pair<String, byte[]>> files = List.of(
        Pair.of("parts", getBytes(partsFile)),
        Pair.of("terminology", getBytes(terminologyFile)),
        Pair.of("supplementary-properties", getBytes(supplementaryPropertiesFile)),
        Pair.of("panels", getBytes(panelsFile)),
        Pair.of("answer-list", getBytes(answerListFile)),
        Pair.of("answer-list-link", getBytes(answerListLinkFile)),
        Pair.of("translations", getBytes(translationsFile)),
        Pair.of("order-observation", getBytes(orderObservationFile)));

    Map<String, Object> params = Map.of("request", req, "files", files);
    return importLogger.runJob(JOB_TYPE, params, loincService::importLoinc);
  }

  private static byte[] getBytes(Publisher<CompletedFileUpload> file) {
    try {
      return FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet());
    } catch (RuntimeException ignored) {}
    return null;
  }
}
