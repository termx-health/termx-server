package com.kodality.termserver.fileimporter.valueset;

import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fhir.valueset.ValueSetFhirImportService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ValueSetFileImportService {
  private final ValueSetFhirImportService valueSetFhirImportService;

  public void process(ValueSetFileImportRequest request, byte[] file) {
    if ("json".equals(request.getType())) {
      valueSetFhirImportService.importValueSet(new String(file, StandardCharsets.UTF_8));
    } else { //TODO fsh file import
      throw ApiError.TE720.toApiException(Map.of("format", request.getType()));
    }
  }
}
