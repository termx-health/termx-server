package com.kodality.termx.terminology.fileimporter.mapset;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.core.utils.FileUtil;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.terminology.fileimporter.mapset.utils.MapSetFileImportRequest;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/file-importer/map-set")
@RequiredArgsConstructor
public class MapSetFileImportController {
  private static final String JOB_TYPE = "map-set-file-import";

  private final ImportLogger importLogger;
  private final MapSetFileImportService importService;

  @Authorized(Privilege.MS_VIEW)
  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public JobLogResponse process(Publisher<CompletedFileUpload> file, @Part("request") String request) {
    MapSetFileImportRequest req = JsonUtil.fromJson(request, MapSetFileImportRequest.class);
    byte[] importFile = file != null ? FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()) : new byte[0];
    return importLogger.runJob(JOB_TYPE, Map.of("request", req, "file", importFile), importService::process);
  }

  @Authorized(Privilege.CS_EDIT)
  @Get(value = "/csv-template", produces = "application/csv")
  public HttpResponse<?> getTemplate() {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(importService.getTemplate());
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template.csv")
        .contentType(MediaType.of("application/csv"));
  }
}
