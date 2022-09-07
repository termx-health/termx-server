package com.kodality.termserver.ts.codesystem.entity;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.codesystem.CodeSystemEntity;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class CodeSystemEntityRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemEntity.class);

  public void save(CodeSystemEntity entity) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entity.getId());
    ssb.property("type", entity.getType());
    ssb.property("code_system", entity.getCodeSystem());

    SqlBuilder sb = ssb.buildSave("terminology.code_system_entity", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entity.setId(id);
  }

  public CodeSystemEntity load(Long id) {
    String sql = "select * from terminology.code_system_entity where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public void batchUpsert(List<CodeSystemEntity> entities, String codeSystem) {
    List<Long> existingIds = jdbcTemplate.queryForList("select id from terminology.code_system_entity where sys_status = 'A' and code_system = ?", Long.class, codeSystem);

    List<CodeSystemEntity> newEntities = entities.stream().filter(e -> e.getId() == null).toList();
    String query = "insert into terminology.code_system_entity (type, code_system) values (?,?)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setString(1, newEntities.get(i).getType());
        ps.setString(2, newEntities.get(i).getCodeSystem());
      }
      @Override
      public int getBatchSize() {
        return newEntities.size();
      }
    });

    SqlBuilder sb = new SqlBuilder("select id from terminology.code_system_entity where sys_status = 'A'");
    sb.and("code_system = ?", codeSystem);
    if (CollectionUtils.isNotEmpty(existingIds)) {
      sb.and().notIn("id", existingIds);
    }
    List<Long> newIds = jdbcTemplate.queryForList(sb.getSql(), Long.class, sb.getParams());

    entities.forEach(e -> {
      if (e.getId() == null && CollectionUtils.isNotEmpty(newIds)) {
        e.setId(newIds.get(0));
        newIds.remove(0);
      }
    });
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.code_system_entity set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}

