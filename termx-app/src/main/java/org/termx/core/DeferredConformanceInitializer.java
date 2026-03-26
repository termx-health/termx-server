package org.termx.core;

import com.kodality.kefhir.core.service.conformance.ConformanceInitializationService;
import com.kodality.kefhir.core.service.conformance.ConformanceInitializer;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@Replaces(ConformanceInitializer.class)
@RequiredArgsConstructor
public class DeferredConformanceInitializer {
  private final ConformanceInitializationService conformanceInitializationService;

  @EventListener
  void onStartup(ServerStartupEvent ignored) {
    conformanceInitializationService.refresh();
  }
}
