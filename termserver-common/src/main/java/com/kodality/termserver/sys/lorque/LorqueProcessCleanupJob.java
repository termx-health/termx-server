package com.kodality.termserver.sys.lorque;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LorqueProcessCleanupJob {
  private final LorqueProcessService service;

  @Scheduled(cron = "0 0 * * *")
  protected void execute() {
    service.cleanup(7);
  }
}
