package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import io.micronaut.core.util.StringUtils;
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

    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and (id = ? or uri = ? or description = ? or exists (select 1 from jsonb_each_text(cs.names) where value = ?))", params.getText(), params.getText(), params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (id ~* ? or uri ~* ? or description ~* ? or exists (select 1 from jsonb_each_text(cs.names) where value ~* ?))", params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains());
    }

    sb.appendIfNotNull("and exists (select 1 from concept c where c.code_system = cs.id and c.sys_status = 'A' and c.code = ?)", params.getConceptCode());

    sb.appendIfNotNull("and exists (select 1 from code_system_version csv where csv.code_system = cs.id and csv.sys_status = 'A' and csv.version = ?)", params.getVersionVersion());
    sb.appendIfNotNull("and exists (select 1 from code_system_version csv where csv.code_system = cs.id and csv.sys_status = 'A' and csv.release_date >= ?)", params.getVersionReleaseDateGe());
    sb.appendIfNotNull("and exists (select 1 from code_system_version csv where csv.code_system = cs.id and csv.sys_status = 'A' and (csv.expiration_date <= ? or expiration_date is null))", params.getVersionExpirationDateLe());

    sb.appendIfNotNull("and exists (select 1 from code_system_entity cse " +
        "inner join code_system_entity_version csev on csev.code_system_entity_id = cse.id and csev.sys_status = 'A' " +
        "where cse.code_system = cs.id and cse.sys_status = 'A' and csev.id = ?)", params.getCodeSystemEntityVersionId());
    return sb;
  }

}
