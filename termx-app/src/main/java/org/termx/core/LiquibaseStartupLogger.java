package org.termx.core;

import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class LiquibaseStartupLogger {
  @Value("${liquibase.datasources.liquibase.enabled:false}")
  boolean enabled;

  @Value("${liquibase.datasources.liquibase.change-log:}")
  String changeLog;

  @Value("${liquibase.datasources.liquibase.default-schema:}")
  String defaultSchema;

  @Value("${datasources.liquibase.url:}")
  String url;

  @Value("${datasources.liquibase.username:}")
  String username;

  @EventListener
  void onStartup(ServerStartupEvent ignored) {
    log.info("Liquibase startup config: enabled={}, changeLog='{}', defaultSchema='{}', datasource='{}', username='{}'",
        enabled, changeLog, defaultSchema, url, username);
  }
}
