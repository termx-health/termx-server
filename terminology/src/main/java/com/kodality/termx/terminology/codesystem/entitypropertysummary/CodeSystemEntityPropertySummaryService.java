package com.kodality.termx.terminology.codesystem.entitypropertysummary;

import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemEntityPropertySummaryService {
  private final CodeSystemEntityPropertySummaryRepository repository;

  public CodeSystemEntityPropertySummary getSummary(String codeSystem, String version) {
    return new CodeSystemEntityPropertySummary(repository.getSummary(codeSystem, version));
  }

  public CodeSystemEntityPropertyConceptSummary getConceptSummary(String codeSystem, String version, Long entityPropertyId) {
    return new CodeSystemEntityPropertyConceptSummary(repository.getConceptSummary(codeSystem, version, entityPropertyId));
  }
}
