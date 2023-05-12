package com.kodality.termserver.terminology.codesystem.compare;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemCompareService {
  private final CodeSystemCompareRepository repository;

  public CodeSystemCompareResult compare(Long sourceCsVersionId, Long targetCsVersionId) {
    if (sourceCsVersionId.equals(targetCsVersionId)) {
      return new CodeSystemCompareResult();
    }
    return repository.compare(sourceCsVersionId, targetCsVersionId);
  }
}
