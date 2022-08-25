package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.CodeSystemVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
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
    SqlBuilder sb = ssb.buildSave("terminology.code_system_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public CodeSystemVersion load(String codeSystem, String versionCode) {
    String sql = "select * from terminology.code_system_version where sys_status = 'A' and code_system = ? and version = ?";
    return getBean(sql, bp, codeSystem, versionCode);
  }

  public CodeSystemVersion load(Long id) {
    String sql = "select * from terminology.code_system_version where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<CodeSystemVersion> query(CodeSystemVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.code_system_version where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.code_system_version where sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and code_system = ?", params.getCodeSystem());
    if (CollectionUtils.isNotEmpty(params.getPermittedCodeSystems())) {
      sb.and().in("code_system", params.getPermittedCodeSystems());
    }
    sb.appendIfNotNull("and version = ?", params.getVersion());
    sb.appendIfNotNull("and status = ?", params.getStatus());
    sb.appendIfNotNull("and release_date <= ?", params.getReleaseDateLe());
    sb.appendIfNotNull("and release_date >= ?", params.getReleaseDateGe());
    sb.appendIfNotNull("and (expiration_date <= ? or expiration_date is null)", params.getExpirationDateLe());
    sb.appendIfNotNull("and (expiration_date >= ? or expiration_date is null)", params.getExpirationDateGe());
    return sb;
  }

  public void activate(String codeSystem, String version) {
    String sql = "update terminology.code_system_version set status = ? where code_system = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, codeSystem, version, PublicationStatus.active);
  }

  public void retire(String codeSystem, String version) {
    String sql = "update terminology.code_system_version set status = ? where code_system = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, codeSystem, version, PublicationStatus.retired);
  }

  public void saveExpirationDate(CodeSystemVersion version) {
    String sql = "update terminology.code_system_version set expiration_date = ? where id = ?";
    jdbcTemplate.update(sql, version.getExpirationDate(), version.getId());
  }

  public void retainVersions(List<CodeSystemVersion> codeSystemVersions, String codeSystem) {
    SqlBuilder sb = new SqlBuilder("update terminology.code_system_version set sys_status = 'C'");
    sb.append(" where code_system = ? and sys_status = 'A'", codeSystem);
    sb.andNotIn("id", codeSystemVersions, CodeSystemVersion::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void unlinkEntityVersions(List<CodeSystemEntityVersion> entityVersions, Long codeSystemVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_version_code_system_version_membership set sys_status = 'C'");
    sb.append(" where code_system_version_id = ? and sys_status = 'A'", codeSystemVersionId);
    sb.andNotIn("code_system_entity_version_id", entityVersions, CodeSystemEntityVersion::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void linkEntityVersions(List<Long> entityVersionIds, Long codeSystemVersionId) {
    if (entityVersionIds == null) {
      return;
    }
    entityVersionIds.forEach(id -> {
      SaveSqlBuilder ssb = new SaveSqlBuilder();
      ssb.property("code_system_entity_version_id", id);
      ssb.property("code_system_version_id", codeSystemVersionId);
      SqlBuilder sb = ssb.buildUpsert("terminology.entity_version_code_system_version_membership", "code_system_entity_version_id", "code_system_version_id");
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }

  public void unlinkEntityVersion(Long codeSystemVersionId, Long entityVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_version_code_system_version_membership set sys_status = 'C' where sys_status = 'A'");
    sb.append("and code_system_version_id = ?", codeSystemVersionId);
    sb.append("and code_system_entity_version_id = ?", entityVersionId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void linkEntityVersion(Long codeSystemVersionId, Long entityVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("code_system_entity_version_id", entityVersionId);
    ssb.property("code_system_version_id", codeSystemVersionId);
    ssb.property("sys_status", "A");
    SqlBuilder sb = ssb.buildUpsert("terminology.entity_version_code_system_version_membership", "code_system_entity_version_id", "code_system_version_id", "sys_status");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public CodeSystemVersion loadLastVersion(String codeSystem, String status) {
    String sql = "select * from terminology.code_system_version where sys_status = 'A' and code_system = ? and status = ? order by release_date desc";
    return getBean(sql, bp, codeSystem, status);
  }

  public CodeSystemVersion loadLastVersionByUri(String uri) {
    String sql = "select * from terminology.code_system_version csv where csv.sys_status = 'A' and " +
        "exists (select 1 from terminology.code_system cs where cs.id = csv.code_system and cs.uri = ? and cs.sys_status = 'A') " +
        "order by csv.release_date desc";
    return getBean(sql, bp, uri);
  }
}
