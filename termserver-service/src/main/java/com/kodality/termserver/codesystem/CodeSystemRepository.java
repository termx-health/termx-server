package com.kodality.termserver.codesystem;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
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

    SqlBuilder sb = ssb.buildUpsert("code_system", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public CodeSystem load(String codeSystem) {
    String sql = "select * from code_system where sys_status = 'A' and id = ?";
    return getBean(sql, bp, codeSystem);
  }

  public QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from code_system cs where cs.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select cs.* from code_system cs where cs.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and id = ?", params.getId());
    sb.appendIfNotNull("and id ~* ?", params.getIdContains());
    sb.appendIfNotNull("and uri = ?", params.getUri());
    sb.appendIfNotNull("and uri ~* ?", params.getUriContains());
    sb.appendIfNotNull("and description = ?", params.getDescription());
    sb.appendIfNotNull("and description ~* ?", params.getDescriptionContains());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(cs.names) where value = ?)", params.getName());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(cs.names) where value ~* ?)", params.getNameContains());
    sb.appendIfNotNull("and exists (select 1 from code_system_entity cse " +
        "inner join code_system_entity_version csev on csev.code_system_entity_id = cse.id and csev.sys_status = 'A' " +
        "where cse.code_system = cs.id and cse.sys_status = 'A' and csev.id = ?)", params.getCodeSystemEntityVersionId());
    return sb;
  }

}
