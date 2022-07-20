package com.kodality.termserver.integration.fileimporter;

import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.integration.fileimporter.processors.FileProcessor;
import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisRequest;
import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class FileImporterService {
  private final BinaryHttpClient client = new BinaryHttpClient();
  private final Map<String, FileProcessor> fileProcessors;

  public FileImporterService(List<FileProcessor> fileProcessors) {
    this.fileProcessors = fileProcessors.stream().collect(Collectors.toMap(FileProcessor::getType, Function.identity()));
  }

  public FileAnalysisResponse analyze(FileAnalysisRequest request) {
    byte[] file = client.GET(request.getLink()).body();

    FileProcessor fp = fileProcessors.get(request.getTemplate());
    return fp.analyze(request.getType(), file);
  }

  public void process(FileProcessingRequest request) {
    byte[] file = client.GET(request.getLink()).body();
    FileProcessor fp = fileProcessors.get(request.getTemplate());
    FileProcessingResponse result = fp.process(request.getType(), file, request.getProperties());

    // todo(marina): save result
  }
}
