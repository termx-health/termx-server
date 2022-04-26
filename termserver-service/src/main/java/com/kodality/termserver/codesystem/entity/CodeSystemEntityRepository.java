package com.kodality.termserver.codesystem.entity;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.codesystem.CodeSystemEntity;
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

