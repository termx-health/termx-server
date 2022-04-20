package com.kodality.termserver.codesystem;

import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.repo.BaseRepository;
import com.kodality.termserver.commons.db.sql.SaveSqlBuilder;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
import com.kodality.termserver.commons.model.model.QueryResult;
import jakarta.inject.Singleton;

@Singleton
public class CodeSystemRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystem.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
  });

  public void create(CodeSystem codeSystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", codeSystem.getId());
    ssb.property("uri", codeSystem.getUri());
    ssb.jsonProperty("names", codeSystem.getNames());
    ssb.property("description", codeSystem.getDescription());

    SqlBuilder sb = ssb.buildInsert("code_system");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public CodeSystem load(String codeSystem) {
    String sql = "select * from code_system where sys_status = 'A' and id = ?";
    return getBean(sql, bp, codeSystem);
  }

  public QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from code_system cs where cs.sys_status = 'A' ");
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select cs.* from code_system cs where cs.sys_status = 'A' ");
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

}
