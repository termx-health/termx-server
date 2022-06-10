package com.kodality.termserver.ts.valueset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ValueSetVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersion.class, bp -> {
    bp.addColumnProcessor("supported_languages", PgBeanProcessor.fromArray());
    bp.addColumnProcessor("rule_set", PgBeanProcessor.fromJson());
  });

  public void save(ValueSetVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("value_set", version.getValueSet());
    ssb.property("version", version.getVersion());
    ssb.property("source", version.getSource());
    ssb.property("supported_languages", "?::text[]", PgUtil.array(version.getSupportedLanguages()));
    ssb.jsonProperty("rule_set", version.getRuleSet());
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

  public ValueSetVersion getVersion(Long id) {
    String sql = "select * from value_set_version where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }
  public List<ValueSetVersion> getVersions(String valueSet) {
    String sql = "select * from value_set_version where sys_status = 'A' and value_set = ?";
    return getBeans(sql, bp, valueSet);
  }

  public ValueSetVersion getLastVersion(String valueSet, String status) {
    String sql = "select * from value_set_version where sys_status = 'A' and value_set = ? and status = ? order by release_date desc";
    return getBean(sql, bp, valueSet, status);
  }

  public QueryResult<ValueSetVersion> query(ValueSetVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from value_set_version vsv where vsv.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from value_set_version vsv where vsv.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and vsv.value_set = ?", params.getValueSet());
    sb.appendIfNotNull("and exists (select 1 from value_set vs where vs.id = vsv.value_set and vs.uri = ? and vs.sys_status = 'A')", params.getValueSetUri());
    sb.appendIfNotNull("and vsv.version = ?", params.getVersion());
    sb.appendIfNotNull("and vsv.status = ?", params.getStatus());
    sb.appendIfNotNull("and (vsv.release_date is null or vsv.release_date <= ?)", params.getReleaseDateLe());
    sb.appendIfNotNull("and (vsv.expiration_date is null or vsv.expiration_date >= ?)", params.getExpirationDateGe());
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

}
