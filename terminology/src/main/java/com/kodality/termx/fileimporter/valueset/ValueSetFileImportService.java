package com.kodality.termx.fileimporter.valueset;

import com.kodality.termx.ApiError;
import com.kodality.termx.fhir.FhirFshConverter;
import com.kodality.termx.fhir.valueset.ValueSetFhirImportService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ValueSetFileImportService {
  private final ValueSetFhirImportService valueSetFhirImportService;
  private final Optional<FhirFshConverter> fhirFshConverter;

  public void process(ValueSetFileImportRequest request, byte[] file) {
    if ("json".equals(request.getType())) {
      valueSetFhirImportService.importValueSet(new String(file, StandardCharsets.UTF_8), request.getValueSetId());
    } else if ("fsh".equals(request.getType())) {
      String json = fhirFshConverter.orElseThrow(ApiError.TE806::toApiException).toFhir(new String(file, StandardCharsets.UTF_8)).join();
      valueSetFhirImportService.importValueSet(json, request.getValueSetId());
    } else {
      throw ApiError.TE720.toApiException(Map.of("format", request.getType()));
    }
  }
}
