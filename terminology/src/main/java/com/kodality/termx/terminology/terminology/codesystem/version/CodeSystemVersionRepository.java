package com.kodality.termx.terminology.terminology.codesystem.version;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import jakarta.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class CodeSystemVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemVersion.class, pb -> {
    pb.addColumnProcessor("description", PgBeanProcessor.fromJson());
    pb.addColumnProcessor("supported_languages", PgBeanProcessor.fromArray());
  });

  private final static String select = "select csv.*, " +
      "(select count(1) from terminology.entity_version_code_system_version_membership evcsvm, terminology.code_system_entity_version csev " +
      "                 where evcsvm.code_system_version_id = csv.id and evcsvm.code_system_entity_version_id = csev.id and evcsvm.sys_status = 'A' and csev.sys_status = 'A') " +
      "as concepts_total ";

  public void save(CodeSystemVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("code_system", version.getCodeSystem());
    ssb.property("version", version.getVersion());
    ssb.property("preferred_language", version.getPreferredLanguage());
    ssb.property("algorithm", version.getAlgorithm());
    ssb.property("supported_languages", "?::text[]", PgUtil.array(version.getSupportedLanguages()));
    ssb.jsonProperty("description", version.getDescription());
    ssb.property("release_date", version.getReleaseDate());
    ssb.property("expiration_date", version.getExpirationDate());
    ssb.property("created", version.getCreated());
    ssb.property("status", version.getStatus());
    SqlBuilder sb = ssb.buildSave("terminology.code_system_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public CodeSystemVersion load(String codeSystem, String versionCode) {
    String sql = select + "from terminology.code_system_version csv where csv.sys_status = 'A' and csv.code_system = ? and csv.version = ?";
    return getBean(sql, bp, codeSystem, versionCode);
  }

  public CodeSystemVersion load(Long id) {
    String sql = select + "from terminology.code_system_version csv where csv.sys_status = 'A' and csv.id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<CodeSystemVersion> query(CodeSystemVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.code_system_version csv " +
          "inner join terminology.code_system cs on cs.id = csv.code_system and cs.sys_status = 'A' " +
          "where csv.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from terminology.code_system_version csv " +
          "inner join terminology.code_system cs on cs.id = csv.code_system and cs.sys_status = 'A' " +
          "where csv.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.and().in("cs.id", params.getCodeSystem());
    sb.and().in("cs.id", params.getPermittedCodeSystems());
    sb.appendIfNotNull("and cs.uri = ?", params.getCodeSystemUri());
    sb.appendIfNotNull("and cs.name = ?", params.getCodeSystemName());
    sb.appendIfNotNull("and terminology.jsonb_search(cs.title) like '%`' || terminology.search_translate(?) || '`%'", params.getCodeSystemTitle());
    sb.appendIfNotNull("and cs.publisher = ?", params.getCodeSystemPublisher());
    sb.appendIfNotNull("and cs.content = ?", params.getCodeSystemContent());
    sb.appendIfNotNull("and terminology.jsonb_search(cs.description) like '%' || terminology.search_translate(?) || '%'", params.getCodeSystemDescriptionContains());
    sb.and().in("csv.id", params.getIds(), Long::valueOf);
    sb.appendIfNotNull("and csv.version = ?", params.getVersion());
    sb.appendIfNotNull("and csv.status = ?", params.getStatus());
    sb.appendIfNotNull("and csv.release_date <= ?", params.getReleaseDateLe());
    sb.appendIfNotNull("and csv.release_date >= ?", params.getReleaseDateGe());
    sb.appendIfNotNull("and (csv.expiration_date <= ? or csv.expiration_date is null)", params.getExpirationDateLe());
    sb.appendIfNotNull("and (csv.expiration_date >= ? or csv.expiration_date is null)", params.getExpirationDateGe());
    sb.appendIfNotNull("and exists (select 1 from terminology.entity_version_code_system_version_membership evcsvm, terminology.code_system_entity_version csev " +
        "where evcsvm.code_system_version_id = csv.id and evcsvm.code_system_entity_version_id = csev.id and evcsvm.sys_status = 'A' and csev.sys_status = 'A' and csev.code = ?)", params.getConceptCode());
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

  public void saveAsDraft(String codeSystem, String version) {
    String sql = "update terminology.code_system_version set status = ? where code_system = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.draft, codeSystem, version, PublicationStatus.draft);
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

  public void linkEntityVersions(List<Long> entityVersionIds, Long codeSystemVersionId) {
    if (entityVersionIds == null) {
      return;
    }
    List<Long> existingEntityVersionIds = jdbcTemplate.queryForList("select code_system_entity_version_id from terminology.entity_version_code_system_version_membership " +
        "where code_system_version_id = ? and sys_status = 'A'", Long.class, codeSystemVersionId);

    List<Long> newEntityVersionIds = entityVersionIds.stream().filter(id -> !existingEntityVersionIds.contains(id)).toList();
    String query = "insert into terminology.entity_version_code_system_version_membership (code_system_entity_version_id, code_system_version_id) select ?,? ";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setLong(1, newEntityVersionIds.get(i));
        ps.setLong(2, codeSystemVersionId);
      }
      @Override public int getBatchSize() {return newEntityVersionIds.size();}
    });
  }

  public void unlinkEntityVersions(List<Long> entityVersionIds, Long codeSystemVersionId) {
    if (entityVersionIds == null) {
      return;
    }
    String query = "update terminology.entity_version_code_system_version_membership set sys_status = 'C' where sys_status = 'A'and code_system_entity_version_id = ? and code_system_version_id = ? ";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setLong(1, entityVersionIds.get(i));
        ps.setLong(2, codeSystemVersionId);
      }
      @Override public int getBatchSize() {return entityVersionIds.size();}
    });
  }

  public CodeSystemVersion loadLastVersion(String codeSystem) {
    String sql = select + "from terminology.code_system_version csv where csv.sys_status = 'A' and csv.code_system = ? and (csv.status = 'active' or csv.status = 'draft') order by csv.release_date desc";
    return getBean(sql, bp, codeSystem);
  }

  public CodeSystemVersion loadLastVersionByUri(String uri) {
    String sql = select + "from terminology.code_system_version csv where csv.sys_status = 'A' and " +
        "exists (select 1 from terminology.code_system cs where cs.id = csv.code_system and cs.uri = ? and cs.sys_status = 'A') " +
        "order by csv.release_date desc";
    return getBean(sql, bp, uri);
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_code_system_version(?)", id);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Boolean.class);
  }
}
