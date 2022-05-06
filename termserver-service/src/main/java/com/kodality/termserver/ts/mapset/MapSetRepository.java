package com.kodality.termserver.ts.mapset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetQueryParams;
import javax.inject.Singleton;

@Singleton
public class MapSetRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSet.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
  });

  public void create(MapSet mapSet) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", mapSet.getId());
    ssb.jsonProperty("names", mapSet.getNames());
    ssb.property("description", mapSet.getDescription());

    SqlBuilder sb = ssb.buildUpsert("map_set", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public MapSet load(String id) {
    String sql = "select * from map_set where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<MapSet> query(MapSetQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from map_set ms where ms.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select ms.* from map_set ms where ms.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(ms.names) where value ~* ?)", params.getName());
    return sb;
  }

}
