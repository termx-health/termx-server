package com.kodality.termserver.ts.valueset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ValueSetVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersion.class, bp -> {
    bp.addColumnProcessor("supported_languages", PgBeanProcessor.fromArray());
  });

  public void save(ValueSetVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("value_set", version.getValueSet());
    ssb.property("version", version.getVersion());
    ssb.property("supported_languages", "?::text[]", PgUtil.array(version.getSupportedLanguages()));
    ssb.property("description", version.getDescription());
    ssb.property("status", version.getStatus());
    ssb.property("release_date", version.getReleaseDate());
    ssb.property("expiration_date", version.getExpirationDate());
    ssb.property("created", version.getCreated());
    SqlBuilder sb = ssb.buildSave("value_set_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public ValueSetVersion getVersion(String valueSet, String version) {
    String sql = "select * from value_set_version where sys_status = 'A' and value_set = ? and version = ?";
    return getBean(sql, bp, valueSet, version);
  }

  public List<ValueSetVersion> getVersions(String valueSet) {
    String sql = "select * from value_set_version where sys_status = 'A' and value_set = ?";
    return getBeans(sql, bp, valueSet);
  }

  public QueryResult<ValueSetVersion> query(ValueSetVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from value_set_version where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from value_set_version where sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and value_set = ?", params.getValueSet());
    sb.appendIfNotNull("and version = ?", params.getVersion());
    sb.appendIfNotNull("and status = ?", params.getStatus());
    sb.appendIfNotNull("and release_date <= ?", params.getReleaseDateLe());
    sb.appendIfNotNull("and expiration_date >= ?", params.getExpirationDateGe());
    return sb;
  }

  public void activate(String valueSet, String version) {
    String sql = "update value_set_version set status = ? where value_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, valueSet, version, PublicationStatus.active);
  }

  public void retire(String valueSet, String version) {
    String sql = "update value_set_version set status = ? where value_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, valueSet, version, PublicationStatus.retired);
  }

  public void retainConcepts(List<Concept> concepts, Long valueSetVersionId) {
    SqlBuilder sb = new SqlBuilder("update concept_value_set_version_membership set sys_status = 'C'");
    sb.append(" where value_set_version_id = ? and sys_status = 'A'", valueSetVersionId);
    sb.andNotIn("concept_id", concepts, Concept::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void upsertConcepts(List<Concept> concepts, Long valueSetVersionId) {
    if (concepts == null) {
      return;
    }
    concepts.forEach(c -> {
      SaveSqlBuilder ssb = new SaveSqlBuilder();
      ssb.property("concept_id", c.getId());
      ssb.property("value_set_version_id", valueSetVersionId);
      SqlBuilder sb = ssb.buildUpsert("concept_value_set_version_membership", "value_set_version_id", "concept_id");
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }

  public void retainDesignations(List<Designation> designations, Long valueSetVersionId) {
    SqlBuilder sb = new SqlBuilder("update designation_value_set_version_membership set sys_status = 'C'");
    sb.append(" where value_set_version_id = ? and sys_status = 'A'", valueSetVersionId);
    sb.andNotIn("designation_id", designations, Designation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void upsertDesignations(List<Designation> designations, Long valueSetVersionId) {
    if (designations == null) {
      return;
    }
    designations.forEach(d -> {
      SaveSqlBuilder ssb = new SaveSqlBuilder();
      ssb.property("designation_id", d.getId());
      ssb.property("value_set_version_id", valueSetVersionId);
      SqlBuilder sb = ssb.buildUpsert("designation_value_set_version_membership", "value_set_version_id", "designation_id");
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }

}
