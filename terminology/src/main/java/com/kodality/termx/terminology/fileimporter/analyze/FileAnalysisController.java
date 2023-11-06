package com.kodality.termx.terminology.fileimporter.analyze;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.utils.FileUtil;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.rxjava3.core.Flowable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/file-importer")
@RequiredArgsConstructor
public class FileAnalysisController {
  private final FileAnalysisService fileAnalysisService;

  @Authorized(privilege = Privilege.CS_EDIT)
  @Post(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA)
  public FileAnalysisResponse analyze(@Nullable Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    FileAnalysisRequest req = JsonUtil.fromJson(request.getValue(), FileAnalysisRequest.class);

    return file != null
        ? fileAnalysisService.analyze(req, FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()))
        : fileAnalysisService.analyze(req);
  }
}
