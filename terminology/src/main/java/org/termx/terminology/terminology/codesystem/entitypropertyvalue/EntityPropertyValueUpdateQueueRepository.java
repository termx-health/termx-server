package org.termx.terminology.terminology.codesystem.entitypropertyvalue;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import jakarta.inject.Singleton;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
public class EntityPropertyValueUpdateQueueRepository extends BaseRepository {

  public void markForUpdate(List<Long> codeSystemEntityVersionIds) {
    if (codeSystemEntityVersionIds == null || codeSystemEntityVersionIds.isEmpty()) {
      return;
    }
    String sql = "insert into terminology.code_system_entity_version_update_queue (code_system_entity_version_id) values (?) " +
        "on conflict (code_system_entity_version_id) where sys_status = 'A' do nothing";
    jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setLong(1, codeSystemEntityVersionIds.get(i));
      }

      @Override
      public int getBatchSize() {
        return codeSystemEntityVersionIds.size();
      }
    });
  }

  public List<Pair<Long, Long>> loadNextBatch(int limit) {
    String sql = "select id, code_system_entity_version_id from terminology.code_system_entity_version_update_queue " +
        "where sys_status = 'A' order by id limit ? for update skip locked";
    return jdbcTemplate.query(sql, (rs, rowNum) -> Pair.of(rs.getLong("id"), rs.getLong("code_system_entity_version_id")), limit);
  }

  public void complete(List<Long> queueIds) {
    if (queueIds == null || queueIds.isEmpty()) {
      return;
    }
    SqlBuilder sb = new SqlBuilder("update terminology.code_system_entity_version_update_queue set sys_status = 'C' where sys_status = 'A'");
    sb.and().in("id", queueIds);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
