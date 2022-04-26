package com.kodality.termserver.mapset.association;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import javax.inject.Singleton;

@Singleton
public class MapSetAssociationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetAssociation.class, bp -> {
    bp.addColumnProcessor("target_code_system_entity_version_id", "target", (rs, index, propType) -> new CodeSystemEntityVersion().setId(rs.getLong("target_code_system_entity_version_id")));
    bp.addColumnProcessor("source_code_system_entity_version_id", "source", (rs, index, propType) -> new CodeSystemEntityVersion().setId(rs.getLong("source_code_system_entity_version_id")));
  });

  public void save(MapSetAssociation association) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", association.getId());
    ssb.property("map_set", association.getMapSet());
    ssb.property("target_code_system_entity_version_id", association.getTarget().getId());
    ssb.property("source_code_system_entity_version_id", association.getSource().getId());
    ssb.property("association_type", association.getAssociationType());
    ssb.property("status", association.getStatus());

    SqlBuilder sb = ssb.buildUpsert("map_set_association", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public MapSetAssociation load(Long id) {
    String sql = "select * from map_set_association where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public MapSetAssociation load(String mapSet, Long id) {
    String sql = "select * from map_set_association where sys_status = 'A' and map_set = ? and id = ?";
    return getBean(sql, bp, mapSet, id);
  }

  public QueryResult<MapSetAssociation> query(MapSetAssociationQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from map_set_association msa where msa.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from map_set_association msa where msa.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetAssociationQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and msa.id = ?", params.getId());
    sb.appendIfNotNull("and msa.map_set = ?", params.getMapSet());
    sb.appendIfNotNull("and msa.status = ?", params.getStatus());
    sb.appendIfNotNull("and msa.association_type = ?", params.getAssociationType());
    if (params.getMapSetVersion() != null) {
      sb.append("and exists (select 1 from map_set_version msv " +
          "inner join entity_version_map_set_version_membership evmsvm on evmsvm.map_set_version_id = msv.id and evmsvm.sys_status = 'A' " +
          "inner join map_set_entity_version msev on msev.id = evmsvm.map_set_entity_version_id and msev.sys_status = 'A' " +
          "where msev.map_set_entity_id = msa.id and msv.version = ? and msv.sys_status = 'A'", params.getMapSetVersion());
      sb.appendIfNotNull("and msv.map_set = ?", params.getMapSet());
      sb.append(")");
    }    return sb;
  }
}
