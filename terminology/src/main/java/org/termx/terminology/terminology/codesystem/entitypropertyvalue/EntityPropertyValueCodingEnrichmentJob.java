package org.termx.terminology.terminology.codesystem.entitypropertyvalue;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Requires(property = "termx.enrichment.entity-property-value-coding.job.enabled", value = "true", defaultValue = "true")
@RequiredArgsConstructor
public class EntityPropertyValueCodingEnrichmentJob {
  private final EntityPropertyValueCodingEnrichmentService service;

  @Scheduled(fixedDelay = "30s", initialDelay = "30s")
  protected void execute() {
    try {
      service.processNextBatch();
    } catch (Exception e) {
      log.error("Failed to process entity property value coding enrichment batch", e);
    }
  }
}
