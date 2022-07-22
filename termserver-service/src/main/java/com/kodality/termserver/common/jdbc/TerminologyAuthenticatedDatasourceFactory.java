package com.kodality.termserver.common.jdbc;

import com.kodality.commons.db.config.AuthenticatedDatasourceFactory;
import com.kodality.termserver.auth.auth.SessionInfo;
import com.kodality.termserver.auth.auth.SessionStore;
import io.micronaut.configuration.jdbc.hikari.DatasourceFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;

@Factory
@Replaces(factory = DatasourceFactory.class)
public class TerminologyAuthenticatedDatasourceFactory extends AuthenticatedDatasourceFactory {

  public TerminologyAuthenticatedDatasourceFactory(ApplicationContext applicationContext) {
    super(applicationContext);
  }

  @Override
  protected String getUser() {
    return SessionStore.get().map(SessionInfo::getUsername).orElse("unknown");
  }
}
