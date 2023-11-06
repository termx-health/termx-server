package com.kodality.termx.implementationguide;

import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideService {
  private final ImplementationGuideRepository repository;

  @Transactional
  public void save(ImplementationGuide ig) {
    repository.save(ig);
  }
}
