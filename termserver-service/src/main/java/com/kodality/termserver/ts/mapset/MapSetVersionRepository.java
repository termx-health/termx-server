package com.kodality.termserver.ts.mapset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetVersion;
import com.kodality.termserver.mapset.MapSetVersionQueryParams;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class MapSetVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetVersion.class, bp -> {
    bp.addColumnProcessor("supported_languages", PgBeanProcessor.fromArray());
  });

  public void save(MapSetVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("map_set", version.getMapSet());
    ssb.property("version", version.getVersion());
    ssb.property("source", version.getSource());
    ssb.property("supported_languages", "?::text[]", PgUtil.array(version.getSupportedLanguages()));
    ssb.property("description", version.getDescription());
    ssb.property("status", version.getStatus());
    ssb.property("release_date", version.getReleaseDate());
    ssb.property("expiration_date", version.getExpirationDate());
    ssb.property("created", version.getCreated());
    SqlBuilder sb = ssb.buildSave("terminology.map_set_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public MapSetVersion getVersion(String mapSet, String version) {
    String sql = "select * from terminology.map_set_version where sys_status = 'A' and map_set = ? and version = ?";
    return getBean(sql, bp, mapSet, version);
  }

  public List<MapSetVersion> getVersions(String mapSet) {
    String sql = "select * from terminology.map_set_version where sys_status = 'A' and map_set = ?";
    return getBeans(sql, bp, mapSet);
  }

  public QueryResult<MapSetVersion> query(MapSetVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.map_set_version where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.map_set_version where sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and map_set = ?", params.getMapSet());
    sb.appendIfNotNull("and version = ?", params.getVersion());
    sb.appendIfNotNull("and status = ?", params.getStatus());
    sb.appendIfNotNull("and release_date <= ?", params.getReleaseDateLe());
    sb.appendIfNotNull("and expiration_date >= ?", params.getExpirationDateGe());
    return sb;
  }

  public void activate(String mapSet, String version) {
    String sql = "update terminology.map_set_version set status = ? where map_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, mapSet, version, PublicationStatus.active);
  }

  public void retire(String mapSet, String version) {
    String sql = "update terminology.map_set_version set status = ? where map_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, mapSet, version, PublicationStatus.retired);
  }

  public void retainEntityVersions(List<MapSetEntityVersion> entityVersions, Long mapSetVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_version_map_set_version_membership set sys_status = 'C'");
    sb.append(" where map_set_version_id = ? and sys_status = 'A'", mapSetVersionId);
    sb.andNotIn("map_set_entity_version_id", entityVersions, MapSetEntityVersion::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void upsertEntityVersions(List<MapSetEntityVersion> entityVersions, Long mapSetVersionId) {
    if (entityVersions == null) {
      return;
    }
    entityVersions.forEach(v -> {
      SaveSqlBuilder ssb = new SaveSqlBuilder();
      ssb.property("map_set_entity_version_id", v.getId());
      ssb.property("map_set_version_id", mapSetVersionId);
      SqlBuilder sb = ssb.buildUpsert("terminology.entity_version_map_set_version_membership", "map_set_entity_version_id", "map_set_version_id");
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }

}
