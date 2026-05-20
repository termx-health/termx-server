package org.termx.editionint.loinc;

import com.kodality.commons.util.JsonUtil;
import org.termx.core.auth.Authorized;
import org.termx.core.sys.job.logger.ImportLogger;
import org.termx.core.utils.FileUtil;
import org.termx.editionint.Privilege;
import org.termx.editionint.loinc.utils.LoincArchiveContents;
import org.termx.editionint.loinc.utils.LoincImportFromArchiveRequest;
import org.termx.editionint.loinc.utils.LoincImportRequest;
import org.termx.sys.job.JobLogResponse;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
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
  private final LoincImportFromArchiveService loincImportFromArchiveService;

  @Authorized(Privilege.CS_WRITE)
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

  /**
   * Streaming counterpart of {@link #process}: the LOINC release zip already lives in the
   * {@code "loinc"} Bob container (uploaded via {@code POST /bob/objects?container=loinc}). The
   * server spools it to a local temp file, unpacks the eight known CSVs by basename, and feeds
   * the existing {@link LoincService#importLoinc} pipeline as an async {@code ImportLogger}
   * job. The browser never has to re-upload a multi-hundred-MB release for retries.
   */
  @Authorized(Privilege.CS_WRITE)
  @Post(value = "/import/from-archive")
  public JobLogResponse processFromArchive(@Body LoincImportFromArchiveRequest request) {
    return loincImportFromArchiveService.startImport(request);
  }

  /**
   * Lists every {@code .csv} entry inside a Bob-stored LOINC archive plus the slot the
   * auto-dispatch would assign each one. Drives the per-slot select boxes on the import
   * page — admins see what's in the chosen zip, the recommended mapping is preselected,
   * and they can override before clicking Import.
   */
  @Authorized(Privilege.CS_WRITE)
  @Get(value = "/archives/{uuid}/files")
  public LoincArchiveContents listArchiveFiles(@PathVariable String uuid,
                                               @Nullable @QueryValue String language) {
    return loincImportFromArchiveService.listArchiveContents(uuid, language);
  }

  private static byte[] getBytes(Publisher<CompletedFileUpload> file) {
    try {
      return FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet());
    } catch (RuntimeException ignored) {}
    return null;
  }
}
