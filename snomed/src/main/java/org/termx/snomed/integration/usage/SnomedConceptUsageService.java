package org.termx.snomed.integration.usage;

import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.termx.snomed.concept.SnomedConceptUsage;

@Singleton
@RequiredArgsConstructor
public class SnomedConceptUsageService {
  private final SnomedConceptUsageRepository repository;

  public List<SnomedConceptUsage> findUsage(List<String> codes) {
    List<String> normalized = normalize(codes);
    if (normalized.isEmpty()) {
      return Collections.emptyList();
    }
    return repository.findAll(normalized);
  }

  private List<String> normalize(List<String> codes) {
    if (codes == null) {
      return List.of();
    }
    return new LinkedHashSet<>(codes.stream()
        .filter(c -> Optional.ofNullable(c).map(String::trim).filter(s -> !s.isEmpty()).isPresent())
        .map(String::trim)
        .toList()).stream().toList();
  }
}
