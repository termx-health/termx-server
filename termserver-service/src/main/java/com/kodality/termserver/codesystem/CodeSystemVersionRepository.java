package com.kodality.termserver.codesystem;

import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.repo.BaseRepository;
import com.kodality.termserver.commons.db.sql.SaveSqlBuilder;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
import com.kodality.termserver.commons.db.util.PgUtil;
import com.kodality.termserver.commons.model.model.QueryResult;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class CodeSystemVersionRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemVersion.class, pb -> {
    pb.addColumnProcessor("supported_languages", PgBeanProcessor.fromArray());
  });

  public void save(CodeSystemVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("code_system", version.getCodeSystem());
    ssb.property("version", version.getVersion());
    ssb.property("source", version.getSource());
    ssb.property("preferred_language", version.getPreferredLanguage());
    ssb.property("supported_languages", "?::text[]", PgUtil.array(version.getSupportedLanguages()));
    ssb.property("description", version.getDescription());
    ssb.property("release_date", version.getReleaseDate());
    ssb.property("expiration_date", version.getExpirationDate());
    ssb.property("created", version.getCreated());
    ssb.property("status", version.getStatus());
    SqlBuilder sb = ssb.buildSave("code_system_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public CodeSystemVersion getVersion(String codeSystem, String versionCode) {
    String sql = "select * from code_system_version where sys_status = 'A' and code_system = ? and version = ?";
    return getBean(sql, bp, codeSystem, versionCode);
  }

  public List<CodeSystemVersion> getVersions(String codeSystem) {
    String sql = "select * from code_system_version where sys_status = 'A' and code_system = ?";
    return getBeans(sql, bp, codeSystem);
  }

  public QueryResult<CodeSystemVersion> query(CodeSystemVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from code_system_version where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from code_system_version where sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and code_system = ?", params.getCodeSystem());
    sb.appendIfNotNull("and version = ?", params.getVersion());
    sb.appendIfNotNull("and status = ?", params.getStatus());
    sb.appendIfNotNull("and release_date <= ?", params.getReleaseDateLe());
    sb.appendIfNotNull("and expiration_date >= ?", params.getExpirationDateGe());
    return sb;
  }

  public void activate(String codeSystem, String version) {
    String sql = "update code_system_version set status = ? where code_system = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, codeSystem, version, PublicationStatus.active);
  }

  public void retire(String codeSystem, String version) {
    String sql = "update code_system_version set status = ? where code_system = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, codeSystem, version, PublicationStatus.retired);
  }

  public void retainEntityVersions(List<CodeSystemEntityVersion> entityVersions, Long codeSystemVersionId) {
    SqlBuilder sb = new SqlBuilder("update entity_version_code_system_version_membership set sys_status = 'C'");
    sb.append(" where code_system_version_id = ? and sys_status = 'A'", codeSystemVersionId);
    sb.andNotIn("code_system_entity_version_id", entityVersions, CodeSystemEntityVersion::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void upsertEntityVersions(List<CodeSystemEntityVersion> entityVersions, Long codeSystemVersionId) {
    if (entityVersions == null) {
      return;
    }
    entityVersions.forEach(v -> {
      SaveSqlBuilder ssb = new SaveSqlBuilder();
      ssb.property("code_system_entity_version_id", v.getId());
      ssb.property("code_system_version_id", codeSystemVersionId);
      SqlBuilder sb = ssb.buildUpsert("entity_version_code_system_version_membership", "code_system_entity_version_id", "code_system_version_id");
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }
}
