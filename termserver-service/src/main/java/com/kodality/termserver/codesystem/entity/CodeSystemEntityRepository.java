package com.kodality.termserver.codesystem.entity;

import com.kodality.termserver.codesystem.CodeSystemEntity;
import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.repo.BaseRepository;
import com.kodality.termserver.commons.db.sql.SaveSqlBuilder;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
import jakarta.inject.Singleton;

@Singleton
public class CodeSystemEntityRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemEntity.class);

  public void save(CodeSystemEntity entity) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entity.getId());
    ssb.property("type", entity.getType());
    ssb.property("code_system", entity.getCodeSystem());

    SqlBuilder sb = ssb.buildSave("code_system_entity", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entity.setId(id);
  }

  public CodeSystemEntity load(Long id) {
    String sql = "select * from code_system_entity where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }
}

