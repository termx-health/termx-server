package com.kodality.termserver.terminology.valueset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
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
    ssb.property("description", version.getDescription());
    ssb.property("status", version.getStatus());
    ssb.property("release_date", version.getReleaseDate());
    ssb.property("expiration_date", version.getExpirationDate());
    ssb.property("created", version.getCreated());
    SqlBuilder sb = ssb.buildSave("terminology.value_set_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public ValueSetVersion load(String valueSet, String version) {
    String sql = "select * from terminology.value_set_version where sys_status = 'A' and value_set = ? and version = ?";
    return getBean(sql, bp, valueSet, version);
  }

  public ValueSetVersion load(Long id) {
    String sql = "select * from terminology.value_set_version where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public ValueSetVersion loadLastVersion(String valueSet) {
    String sql = "select * from terminology.value_set_version where sys_status = 'A' and value_set = ? and (status = 'active' or status = 'draft') order by release_date desc";
    return getBean(sql, bp, valueSet);
  }

  public QueryResult<ValueSetVersion> query(ValueSetVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.value_set_version vsv where vsv.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.value_set_version vsv where vsv.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and vsv.value_set = ?", params.getValueSet());
    if (CollectionUtils.isNotEmpty(params.getPermittedValueSets())) {
      sb.and().in("vsv.value_set", params.getPermittedValueSets());
    }
    sb.appendIfNotNull("and exists (select 1 from terminology.value_set vs where vs.id = vsv.value_set and vs.uri = ? and vs.sys_status = 'A')", params.getValueSetUri());
    sb.appendIfNotNull("and vsv.version = ?", params.getVersion());
    sb.appendIfNotNull("and vsv.status = ?", params.getStatus());
    sb.appendIfNotNull("and (vsv.release_date is null or vsv.release_date <= ?)", params.getReleaseDateLe());
    sb.appendIfNotNull("and (vsv.expiration_date is null or vsv.expiration_date >= ?)", params.getExpirationDateGe());
    return sb;
  }

  public void activate(String valueSet, String version) {
    String sql = "update terminology.value_set_version set status = ? where value_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, valueSet, version, PublicationStatus.active);
  }

  public void retire(String valueSet, String version) {
    String sql = "update terminology.value_set_version set status = ? where value_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, valueSet, version, PublicationStatus.retired);
  }

  public void saveAsDraft(String valueSet, String version) {
    String sql = "update terminology.value_set_version set status = ? where value_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.draft, valueSet, version, PublicationStatus.draft);
  }

  public void saveExpirationDate(ValueSetVersion version) {
    String sql = "update terminology.value_set_version set expiration_date = ? where id = ?";
    jdbcTemplate.update(sql, version.getExpirationDate(), version.getId());
  }

  public ValueSetVersion loadLastVersionByUri(String uri) {
    String sql = "select * from terminology.value_set_version vsv where vsv.sys_status = 'A' and (vsv.status = 'active' or vsv.status = 'draft') and " +
        "exists (select 1 from terminology.value_set vs where vs.id = vsv.value_set and vs.uri = ? and vs.sys_status = 'A') " +
        "order by vsv.release_date desc";
    return getBean(sql, bp, uri);
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_value_set_version(?)", id);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }

}
