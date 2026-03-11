package com.kodality.commons.db.config;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.configuration.jdbc.hikari.HikariUrlDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Supplier;

public class AuthenticatedDataSource extends HikariUrlDataSource {
  private final Supplier<String> usernameProvider;

  public AuthenticatedDataSource(DatasourceConfiguration conf, Supplier<String> usernameProvider) {
    super(conf);
    this.usernameProvider = usernameProvider;
  }

  @Override
  public Connection getConnection() throws SQLException {
    Connection c = super.getConnection();
    try (PreparedStatement ps = c.prepareStatement("select core.set_user(?)")) {
      ps.setString(1, usernameProvider.get());
      ps.execute();
    }
    return c;
  }

}
