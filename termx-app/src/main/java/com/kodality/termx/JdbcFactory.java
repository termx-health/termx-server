package com.kodality.termx;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

@Factory
public class JdbcFactory {
  @Inject
  private DataSource dataSource;

  @Bean
  public JdbcTemplate getJdbcTemplate() {
    return new JdbcTemplate(dataSource);
  }
}
