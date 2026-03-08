package org.termx.core.sequence;

import jakarta.inject.Singleton;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SequenceService {
  private final SequenceRepository sequenceRepository;

  public String getNextValue(String sequence, String scope, LocalDate date, String tenant) {
    return sequenceRepository.getNextValue(sequence, scope, date, tenant);
  }
}
