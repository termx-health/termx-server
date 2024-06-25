package com.kodality.termx.terminology.terminology.codesystem.entitypropertysummary;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemEntityPropertySummaryService {
  private final CodeSystemEntityPropertySummaryRepository repository;

  public CodeSystemEntityPropertySummary getSummary(String codeSystem, String version, String entityPropertyValues) {
    return new CodeSystemEntityPropertySummary(repository.getSummary(codeSystem, version, entityPropertyValues));
  }

  public CodeSystemEntityPropertyConceptSummary getConceptSummary(String codeSystem, String version, Long entityPropertyId, String entityPropertyValues) {
    return new CodeSystemEntityPropertyConceptSummary(repository.getConceptSummary(codeSystem, version, entityPropertyId, entityPropertyValues));
  }
}
