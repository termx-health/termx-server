package com.kodality.termserver.integration.fileimporter.mapset;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.integration.fileimporter.mapset.utils.MapSetFileImportRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.Flowable;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/file-importer/map-set")
@RequiredArgsConstructor
public class MapSetFileImportController {
  private final MapSetFileImportService importService;

  @Post(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA)
  public void process(Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    CompletedFileUpload importFile = Flowable.fromPublisher(file).firstOrError().blockingGet();
    MapSetFileImportRequest req = JsonUtil.fromJson(request.getValue(), MapSetFileImportRequest.class);

    try {
      importService.process(req, importFile.getBytes());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
