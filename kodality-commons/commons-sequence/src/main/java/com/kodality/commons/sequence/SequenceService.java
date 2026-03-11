package com.kodality.commons.sequence;

import java.time.LocalDate;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SequenceService {
  private final SequenceRepository sequenceRepository;

  public String getNextValue(String sequence, String scope, LocalDate date, String tenant) {
    return sequenceRepository.getNextValue(sequence, scope, date, tenant);
  }
}
