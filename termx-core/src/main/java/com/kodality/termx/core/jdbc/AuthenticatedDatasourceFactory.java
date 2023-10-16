package com.kodality.termx.core.jdbc;

import com.kodality.commons.db.config.AuthenticatedDataSource;
import com.kodality.commons.micronaut.BeanContext;
import com.kodality.termx.core.auth.SessionInfo;
import com.kodality.termx.core.auth.SessionStore;
import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.configuration.jdbc.hikari.DatasourceFactory;
import io.micronaut.configuration.jdbc.hikari.HikariUrlDataSource;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Factory
@Replaces(factory = DatasourceFactory.class)
public class AuthenticatedDatasourceFactory {
  private final List<HikariUrlDataSource> dataSources = new ArrayList<>(2);

  public AuthenticatedDatasourceFactory(BeanContext bc) {
    // bc required to run migrations
  }

  @Context
  @EachBean(DatasourceConfiguration.class)
  public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
    if (datasourceConfiguration.getName().equals("liquibase")) {
      HikariUrlDataSource ds = new HikariUrlDataSource(datasourceConfiguration);
      dataSources.add(ds);
      return ds;
    }
    AuthenticatedDataSource ds = new AuthenticatedDataSource(datasourceConfiguration, () -> SessionStore.get().map(SessionInfo::getUsername).orElse("unknown"));
    dataSources.add(ds);
    return ds;
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
