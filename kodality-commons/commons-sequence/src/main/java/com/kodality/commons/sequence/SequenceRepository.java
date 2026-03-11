package com.kodality.commons.sequence;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Singleton
public class SequenceRepository {
  @Inject
  private JdbcTemplate jdbcTemplate;

  public String getNextValue(String code, String scope, LocalDate date, String tenant) {
    try {
      Long sequenceId = jdbcTemplate.queryForObject("select core.sequence_id(?, ?, ?)", Long.class, code, scope, tenant);
      return jdbcTemplate.queryForObject("select core.sequence_nextval(?, ?)", String.class, sequenceId, date);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

}
