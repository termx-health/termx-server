package com.kodality.termserver.codesystem.entity;

import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.repo.BaseRepository;
import com.kodality.termserver.commons.db.sql.SaveSqlBuilder;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
import com.kodality.termserver.commons.model.model.QueryResult;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class CodeSystemEntityVersionRepository  extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemEntityVersion.class);

  public void save(CodeSystemEntityVersion version, Long codeSystemEntityId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("code_system_entity_id", codeSystemEntityId);
    ssb.property("code", version.getCode());
    ssb.property("description", version.getDescription());
    ssb.property("status", version.getStatus());
    ssb.property("created", version.getCreated());

    SqlBuilder sb = ssb.buildSave("code_system_entity_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public List<CodeSystemEntityVersion> loadAll(Long codeSystemEntityId) {
    String sql = "select * from code_system_entity_version where sys_status = 'A' and code_system_entity_id = ? and status = 'active'";
    return getBeans(sql, bp, codeSystemEntityId);
  }

  public CodeSystemEntityVersion getByCode(String code) {
    String sql = "select * from code_system_entity_version where sys_status = 'A' and code = ?";
    return getBean(sql, bp, code);
  }

  public QueryResult<CodeSystemEntityVersion> query(CodeSystemEntityVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from code_system_entity_version csev where csev.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from code_system_entity_version csev where csev.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemEntityVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and csev.code_system_entity_id = ?", params.getCodeSystemEntityId());
    sb.appendIfNotNull("and exists (select 1 from entity_version_code_system_version_membership evcsvm " +
        "where evcsvm.code_system_entity_version_id = csev.id and evcsvm.code_system_version_id = ?", params.getCodeSystemVersionId());
    sb.appendIfNotNull("and csev.status = ?", params.getStatus());
    sb.appendIfNotNull("and status = ?", params.getStatus());
    sb.appendIfNotNull("and code = ?", params.getCode());
    return sb;
  }
}

