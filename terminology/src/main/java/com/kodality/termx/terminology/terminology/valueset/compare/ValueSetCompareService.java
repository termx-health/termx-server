package com.kodality.termx.terminology.terminology.valueset.compare;

import com.kodality.termx.ts.valueset.ValueSetCompareResult;
import java.util.Objects;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetCompareService {
  private final ValueSetCompareRepository repository;

  public ValueSetCompareResult compare(Long sourceVsVersionId, Long targetVsVersionId) {
    if (Objects.equals(sourceVsVersionId, targetVsVersionId)) {
      return new ValueSetCompareResult();
    }
    return repository.compare(sourceVsVersionId, targetVsVersionId);
  }
}
