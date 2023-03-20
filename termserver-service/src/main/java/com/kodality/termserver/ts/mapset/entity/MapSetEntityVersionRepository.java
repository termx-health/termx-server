package com.kodality.termserver.ts.mapset.entity;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.mapset.MapSetEntityVersion;
import com.kodality.termserver.ts.mapset.MapSetEntityVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
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

    SqlBuilder sb = ssb.buildSave("terminology.map_set_entity_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public MapSetEntityVersion load(Long versionId) {
    String sql = "select msev.*, mse.map_set from terminology.map_set_entity_version msev " +
        "inner join terminology.map_set_entity mse on mse.id = msev.map_set_entity_id and mse.sys_status = 'A' " +
        "where msev.sys_status = 'A' and msev.id = ?";
    return getBean(sql, bp, versionId);
  }

  public void retainVersions(List<MapSetEntityVersion> versions, Long mapSetEntityId) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_entity_version set sys_status = 'C'");
    sb.append(" where map_set_entity_id = ? and sys_status = 'A'", mapSetEntityId);
    sb.andNotIn("id", versions, MapSetEntityVersion::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public QueryResult<MapSetEntityVersion> query(MapSetEntityVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.map_set_entity_version msev " +
          "inner join terminology.map_set_entity mse on mse.id = msev.map_set_entity_id and mse.sys_status = 'A' " +
          "where msev.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select msev.*, mse.map_set from terminology.map_set_entity_version msev " +
          "inner join terminology.map_set_entity mse on mse.id = msev.map_set_entity_id and mse.sys_status = 'A' " +
          "where msev.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetEntityVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and msev.map_set_entity_id = ?", params.getMapSetEntityId());
    sb.appendIfNotNull("and msev.status = ?", params.getStatus());
    sb.appendIfNotNull("and mse.map_set = ?", params.getMapSet());
    if (CollectionUtils.isNotEmpty(params.getPermittedMapSets())) {
      sb.and().in("mse.map_set", params.getPermittedMapSets());
    }
    sb.appendIfNotNull("and exists (select 1 from terminology.entity_version_map_set_version_membership evmsvm " +
        "where evmsvm.map_set_entity_version_id = msev.id and evmsvm.map_set_version_id = ?)", params.getMapSetVersionId());
    sb.appendIfNotNull("and msev.description ~* ?", params.getDescriptionContains());
    if (params.getMapSetVersion() != null) {
      sb.append("and exists (select 1 from terminology.map_set_version msv " +
          "inner join terminology.entity_version_map_set_version_membership evmsvm on evmsvm.map_set_version_id = msv.id and evmsvm.sys_status = 'A' " +
          "where evmsvm.map_set_entity_version_id = msev.id and msv.version = ? and msv.sys_status = 'A'", params.getMapSetVersion());
      sb.appendIfNotNull("and msv.map_set = ?", params.getMapSet());
      sb.append(")");
    }
    return sb;
  }

  public void activate(Long versionId) {
    String sql = "update terminology.map_set_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, versionId, PublicationStatus.active);
  }

  public void retire(Long versionId) {
    String sql = "update terminology.map_set_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, versionId, PublicationStatus.retired);
  }

  public void cancel(Long versionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_entity_version set sys_status = 'C' where id = ? and sys_status = 'A'", versionId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}


