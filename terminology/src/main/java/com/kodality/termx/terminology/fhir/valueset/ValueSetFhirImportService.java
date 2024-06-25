package com.kodality.termx.terminology.fhir.valueset;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.core.http.BinaryHttpClient;
import com.kodality.termx.terminology.terminology.valueset.ValueSetImportService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.valueset.ValueSetImportAction;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.nio.charset.StandardCharsets;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ValueSetFhirImportService {
  private final ValueSetImportService importService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSetImportAction action = new ValueSetImportAction().setActivate(PublicationStatus.active.equals(valueSet.getStatus()));
    importService.importValueSet(ValueSetFhirMapper.fromFhirValueSet(valueSet), action);
  }

  @Transactional
  public void importValueSetFromUrl(String url, String id) {
    String resource = getResource(url);
    importValueSet(resource, id);
  }

  @Transactional
  public void importValueSet(String resource, String id) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhir = FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    if (!ResourceType.valueSet.equals(fhir.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    fhir.setId(id);
    importValueSet(fhir);
  }

  private String getResource(String url) {
    log.info("Loading fhir value set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.UTF_8);
  }
}
