package com.kodality.termserver.mapset.entity;

import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.repo.BaseRepository;
import com.kodality.termserver.commons.db.sql.SaveSqlBuilder;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
import com.kodality.termserver.commons.model.model.QueryResult;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetEntityVersionQueryParams;
import javax.inject.Singleton;

@Singleton
public class MapSetEntityVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetEntityVersion.class);

  public void save(MapSetEntityVersion version, Long mapSetEntityId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("map_set_entity_id", mapSetEntityId);
    ssb.property("description", version.getDescription());
    ssb.property("status", version.getStatus());
    ssb.property("created", version.getCreated());

    SqlBuilder sb = ssb.buildSave("map_set_entity_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public QueryResult<MapSetEntityVersion> query(MapSetEntityVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from map_set_entity_version msev where msev.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from map_set_entity_version msev where msev.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetEntityVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and msev.map_set_entity_id = ?", params.getMapSetEntityId());
    sb.appendIfNotNull("and msev.status = ?", params.getStatus());
    sb.appendIfNotNull("and exists (select 1 from map_set_entity mse " +
        "where mse.id = msev.map_set_entity_id and map_set = ?)", params.getMapSet());
    sb.appendIfNotNull("and exists (select 1 from entity_version_map_set_version_membership evmsvm " +
        "where evmsvm.map_set_entity_version_id = msev.id and evmsvm.map_set_version_id = ?)", params.getMapSetVersionId());
    if (params.getMapSetVersion() != null) {
      sb.append("and exists (select 1 from map_set_version msv " +
          "inner join entity_version_map_set_version_membership evmsvm on evmsvm.map_set_version_id = msv.id and evmsvm.sys_status = 'A' " +
          "where evmsvm.map_set_entity_version_id = msev.id and msv.version = ? and msv.sys_status = 'A'", params.getMapSetVersion());
      sb.appendIfNotNull("and msv.map_set = ?", params.getMapSet());
      sb.append(")");
    }
    return sb;
  }

  public MapSetEntityVersion getVersion(Long versionId) {
    String sql = "select * from map_set_entity_version where sys_status = 'A' and id = ?";
    return getBean(sql, bp, versionId);
  }

  public void activate(Long versionId) {
    String sql = "update map_set_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, versionId, PublicationStatus.active);
  }

  public void retire(Long versionId) {
    String sql = "update map_set_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, versionId, PublicationStatus.retired);
  }
}


