package com.kodality.termx.terminology.mapset.statistics;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetVersionStatistics;
import javax.inject.Singleton;

@Singleton
public class MapSetStatisticsRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetVersionStatistics.class, bp -> {
    bp.addColumnProcessor("map_set_version", PgBeanProcessor.fromJson());
  });

  private final static String select = "select mss.*, " +
      "(select json_build_object('id', msv.id, 'version', msv.version) from terminology.map_set_version msv where msv.id = mss.map_set_version_id and msv.sys_status = 'A') as map_set_version ";

  public void save(MapSetVersionStatistics statistics) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", statistics.getId());
    ssb.property("map_set", statistics.getMapSet());
    ssb.property("map_set_version_id", statistics.getMapSetVersion().getId());
    ssb.property("source_concepts", statistics.getSourceConcepts());
    ssb.property("equivalent", statistics.getEquivalent());
    ssb.property("no_map", statistics.getNoMap());
    ssb.property("narrower", statistics.getNarrower());
    ssb.property("broader", statistics.getBroader());
    ssb.property("unmapped", statistics.getUnmapped());
    ssb.property("inactive_sources", statistics.getInactiveSources());
    ssb.property("inactive_targets", statistics.getInactiveTargets());
    ssb.property("created_at", statistics.getCreatedAt());
    ssb.property("created_by", statistics.getCreatedBy());
    SqlBuilder sb = ssb.buildSave("terminology.map_set_statistics", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    statistics.setId(id);
  }

  public MapSetVersionStatistics load(String mapSet, Long mapSetVersionId) {
    String sql = select + "from terminology.map_set_statistics mss where mss.sys_status = 'A' and mss.map_set = ? and mss.map_set_version_id = ?";
    return getBean(sql, bp, mapSet, mapSetVersionId);
  }
}
