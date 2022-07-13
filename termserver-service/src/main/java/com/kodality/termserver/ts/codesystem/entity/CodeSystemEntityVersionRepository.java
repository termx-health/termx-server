package com.kodality.termserver.ts.codesystem.entity;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import io.micronaut.core.util.StringUtils;
import javax.inject.Singleton;

@Singleton
public class CodeSystemEntityVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemEntityVersion.class);

  public void save(CodeSystemEntityVersion version, Long codeSystemEntityId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("code_system_entity_id", codeSystemEntityId);
    ssb.property("code", version.getCode());
    ssb.property("description", version.getDescription());
    ssb.property("status", version.getStatus());
    ssb.property("created", version.getCreated());

    SqlBuilder sb = ssb.buildSave("terminology.code_system_entity_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public CodeSystemEntityVersion load(Long id) {
    String sql = "select csev.*, cse.code_system from terminology.code_system_entity_version csev " +
        "inner join terminology.code_system_entity cse on cse.id = csev.code_system_entity_id and cse.sys_status = 'A' " +
        "where csev.sys_status = 'A' and csev.id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<CodeSystemEntityVersion> query(CodeSystemEntityVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.code_system_entity_version csev " +
          "inner join terminology.code_system_entity cse on cse.id = csev.code_system_entity_id and cse.sys_status = 'A' " +
          "where csev.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select csev.*, cse.code_system from terminology.code_system_entity_version csev " +
          "inner join terminology.code_system_entity cse on cse.id = csev.code_system_entity_id and cse.sys_status = 'A' " +
          "where csev.sys_status = 'A'");
      sb.append(filter(params));
      sb.append("order by created");
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemEntityVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and csev.code_system_entity_id = ?", params.getCodeSystemEntityId());
    sb.appendIfNotNull("and csev.status = ?", params.getStatus());
    sb.appendIfNotNull("and csev.code ~* ?", params.getCodeContains());
    sb.appendIfNotNull("and csev.description ~* ?", params.getDescriptionContains());
    if (StringUtils.isNotEmpty(params.getCode())) {
      sb.and().in("csev.code ", params.getCode());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (csev.code ~* ? or csev.description ~* ? or " +
          "exists(select 1 from terminology.designation d where d.code_system_entity_version_id = csev.id and d.name ~* ? ))", params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    sb.appendIfNotNull("and cse.code_system = ?", params.getCodeSystem());
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system cs " +
        "where cse.code_system = cs.id and cs.uri = ? and cs.sys_status = 'A')", params.getCodeSystemUri());
    sb.appendIfNotNull("and exists (select 1 from terminology.entity_version_code_system_version_membership evcsvm " +
        "where evcsvm.code_system_entity_version_id = csev.id and evcsvm.code_system_version_id = ?)", params.getCodeSystemVersionId());
    if (params.getCodeSystemVersion() != null) {
      sb.append("and exists (select 1 from terminology.code_system_version csv " +
          "inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A' " +
          "where evcsvm.code_system_entity_version_id = csev.id and csv.version = ? and csv.sys_status = 'A'", params.getCodeSystemVersion());
      sb.appendIfNotNull("and csv.code_system = ?", params.getCodeSystem());
      sb.append(")");
    }
    return sb;
  }

  public void activate(Long versionId) {
    String sql = "update terminology.code_system_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, versionId, PublicationStatus.active);
  }

  public void retire(Long versionId) {
    String sql = "update terminology.code_system_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, versionId, PublicationStatus.retired);
  }
}

