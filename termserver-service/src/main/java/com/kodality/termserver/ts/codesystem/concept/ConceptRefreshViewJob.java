package com.kodality.termserver.ts.codesystem.concept;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptRefreshViewJob {
  private final ConceptRepository repository;

  private boolean refresh;

  @Scheduled(fixedDelay = "5m")
  protected void execute() {
    if (this.refresh) {
      log.info("Concept closure view refresh started");
      long start = System.currentTimeMillis();
      repository.refreshClosureView();
      log.info("Concept closure view refresh took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
      this.refresh = false;
    }
  }

  public void refreshView() {
    this.refresh = true;
  }
}
