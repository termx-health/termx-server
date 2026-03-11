package com.kodality.commons.db.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.configuration.jdbc.hikari.HikariUrlDataSource;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

@Slf4j
//@Factory
//@Replaces(factory = DatasourceFactory.class)
public abstract class AuthenticatedDatasourceFactory {

  private final ApplicationContext applicationContext;
  private final List<HikariUrlDataSource> dataSources = new ArrayList<>(2);

  public AuthenticatedDatasourceFactory(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  protected abstract String getUser();

  @Context
  @EachBean(DatasourceConfiguration.class)
  public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
    if (datasourceConfiguration.getName().equals("liquibase")) {
      HikariUrlDataSource ds = new HikariUrlDataSource(datasourceConfiguration);
      addMeterRegistry(ds);
      dataSources.add(ds);
      return ds;
    }
    AuthenticatedDataSource ds = new AuthenticatedDataSource(datasourceConfiguration, this::getUser);
    addMeterRegistry(ds);
    dataSources.add(ds);
    return ds;
  }

  private void addMeterRegistry(HikariUrlDataSource ds) {
    try {
      MeterRegistry meterRegistry = getMeterRegistry();
      if (ds != null && meterRegistry != null &&
          this.applicationContext.getProperty(MICRONAUT_METRICS_BINDERS + ".jdbc.enabled", boolean.class).orElse(true)) {
        ds.setMetricRegistry(meterRegistry);
      }
    } catch (NoClassDefFoundError ignore) {
      log.info("Could not wire metrics to HikariCP as there is no class of type MeterRegistry on the classpath," +
          " io.micronaut.configuration:micrometer-core library missing.");
    }
  }

  private MeterRegistry getMeterRegistry() {
    return this.applicationContext.containsBean(MeterRegistry.class) ?
        this.applicationContext.getBean(MeterRegistry.class) : null;
  }

  @PreDestroy
  public void close() {
    for (HikariUrlDataSource dataSource : dataSources) {
      try {
        dataSource.close();
      } catch (Exception e) {
        if (log.isWarnEnabled()) {
          log.warn("Error closing data source [" + dataSource + "]: " + e.getMessage(), e);
        }
      }
    }
  }

}
