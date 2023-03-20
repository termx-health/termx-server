package com.kodality.termserver.jdbc;

import com.kodality.commons.db.config.AuthenticatedDataSource;
import com.kodality.commons.micronaut.BeanContext;
import com.kodality.termserver.auth.CommonSessionInfo;
import com.kodality.termserver.auth.CommonSessionProvider;
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
  private final CommonSessionProvider sessionProvider;

  public AuthenticatedDatasourceFactory(BeanContext bc, CommonSessionProvider sessionProvider) {
    // bc required to run migrations
    this.sessionProvider = sessionProvider;
  }

  @Context
  @EachBean(DatasourceConfiguration.class)
  public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
    if (datasourceConfiguration.getName().equals("liquibase")) {
      return new HikariUrlDataSource(datasourceConfiguration);
    }
    return new AuthenticatedDataSource(datasourceConfiguration, () -> sessionProvider.get().map(CommonSessionInfo::getUsername).orElse("unknown"));
  }

}
