package com.kodality.termserver.integration.fileimporter;

import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisRequest;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/file-importer")
@RequiredArgsConstructor
public class FileImporterController {
  private final FileImporterService fileImporterService;

  @Post(value = "/analyze")
  public HttpResponse<?> analyze(@Body FileAnalysisRequest request) {
    // todo (roman): add file upload
    return HttpResponse.ok(fileImporterService.analyze(request));
  }

  @Post(value = "/process")
  public void process(@Body FileProcessingRequest request) {
    fileImporterService.process(request);
  }
}
