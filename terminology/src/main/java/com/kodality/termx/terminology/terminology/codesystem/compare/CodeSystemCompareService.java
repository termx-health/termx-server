package com.kodality.termx.terminology.terminology.codesystem.compare;

import jakarta.inject.Singleton;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemCompareService {
  private final CodeSystemCompareRepository repository;

  public CodeSystemCompareResult compare(Long sourceCsVersionId, Long targetCsVersionId) {
    if (Objects.equals(sourceCsVersionId, targetCsVersionId)) {
      return new CodeSystemCompareResult();
    }
    return repository.compare(sourceCsVersionId, targetCsVersionId);
  }
}
