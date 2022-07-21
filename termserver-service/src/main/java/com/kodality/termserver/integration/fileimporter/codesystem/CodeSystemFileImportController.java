package com.kodality.termserver.integration.fileimporter.codesystem;

import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileAnalysisRequest;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/file-importer/code-system")
@RequiredArgsConstructor
public class CodeSystemFileImportController {
  private final CodeSystemFileImportService fileImporterService;

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
