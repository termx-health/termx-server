package com.kodality.termserver.integration.fileimporter;

import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisRequest;
import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessor;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class FileImporterService {
  private final BinaryHttpClient client = new BinaryHttpClient();


  public FileAnalysisResponse analyze(FileAnalysisRequest request) {
    byte[] file = client.GET(request.getLink()).body();

    FileProcessor fp = new FileProcessor();
    return fp.analyze(request.getType(), file);
  }

  public void process(FileProcessingRequest request) {
    byte[] file = client.GET(request.getLink()).body();
    FileProcessor fp = new FileProcessor();
    FileProcessingResponse result = fp.process(request.getType(), file, request.getProperties());

    // todo(marina): save result
  }
}
