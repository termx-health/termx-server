package org.termx.core.sequence;

import com.kodality.commons.db.repo.BaseRepository;
import jakarta.inject.Singleton;
import java.time.LocalDate;

@Singleton
public class SequenceRepository extends BaseRepository {

  public String getNextValue(String code, String scope, LocalDate date, String tenant) {
    Long sequenceId = jdbcTemplate.queryForObject("select core.sequence_id(?, ?, ?)", Long.class, code, scope, tenant);
    return jdbcTemplate.queryForObject("select core.sequence_nextval(?, ?)", String.class, sequenceId, date);
  }
}
