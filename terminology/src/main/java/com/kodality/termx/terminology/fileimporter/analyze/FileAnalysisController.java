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
  public FileAnalysisResponse analyze(@Nullable Publisher<CompletedFileUpload> file, @Part("request") String request) {
    FileAnalysisRequest req = JsonUtil.fromJson(request, FileAnalysisRequest.class);

    CompletedFileUpload fileUpload = file != null ? Flowable.fromPublisher(file).firstElement().blockingGet() : null;
    byte[] importFile = fileUpload != null ? FileUtil.readBytes(fileUpload) : null;

    return importFile != null
        ? fileAnalysisService.analyze(req, importFile)
        : fileAnalysisService.analyze(req);
  }
}
