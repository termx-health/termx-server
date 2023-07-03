package com.kodality.termx.fileimporter.valueset;

import com.kodality.termx.ApiError;
import com.kodality.termx.fhir.valueset.ValueSetFhirImportService;
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
      valueSetFhirImportService.importValueSet(new String(file, StandardCharsets.UTF_8), request.getValueSetId());
    } else { //TODO fsh file import
      throw ApiError.TE720.toApiException(Map.of("format", request.getType()));
    }
  }
}
