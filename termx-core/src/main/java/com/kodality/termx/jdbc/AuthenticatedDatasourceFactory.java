package com.kodality.termx.jdbc;

import com.kodality.commons.db.config.AuthenticatedDataSource;
import com.kodality.commons.micronaut.BeanContext;
import com.kodality.termx.auth.SessionInfo;
import com.kodality.termx.auth.SessionStore;
import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.configuration.jdbc.hikari.DatasourceFactory;
import io.micronaut.configuration.jdbc.hikari.HikariUrlDataSource;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Factory
@Replaces(factory = DatasourceFactory.class)
public class AuthenticatedDatasourceFactory {

  public AuthenticatedDatasourceFactory(BeanContext bc) {
    // bc required to run migrations
  }

  @Context
  @EachBean(DatasourceConfiguration.class)
  public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
    if (datasourceConfiguration.getName().equals("liquibase")) {
      return new HikariUrlDataSource(datasourceConfiguration);
    }
    return new AuthenticatedDataSource(datasourceConfiguration, () -> SessionStore.get().map(SessionInfo::getUsername).orElse("unknown"));
  }

}