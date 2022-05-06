package com.kodality.termserver.ts.mapset.entity;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.mapset.MapSetEntity;
import javax.inject.Singleton;

@Singleton
public class MapSetEntityRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetEntity.class);

  public void save(MapSetEntity entity) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entity.getId());
    ssb.property("map_set", entity.getMapSet());

    SqlBuilder sb = ssb.buildSave("map_set_entity", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entity.setId(id);
  }

  public MapSetEntity load(Long id) {
    String sql = "select * from map_set_entity where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }
}
