package com.kodality.termserver.commons.db.repo;

import javax.inject.Inject;
import org.springframework.jdbc.core.JdbcTemplate;

public class BaseRepository extends AbstractBaseRepository {
  @Inject
  protected JdbcTemplate jdbcTemplate;

  @Override
  protected JdbcTemplate getJdbcTemplate() {
    return jdbcTemplate;
  }
}
