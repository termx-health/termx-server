package com.kodality.termx.terminology.mapset.property;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.mapset.MapSetProperty;
import com.kodality.termx.ts.mapset.MapSetPropertyQueryParams;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class MapSetPropertyRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetProperty.class, p -> {
    p.addColumnProcessor("rule", PgBeanProcessor.fromJson());
    p.addColumnProcessor("description", PgBeanProcessor.fromJson());
  });

  private final Map<String, String> orderMapping = Map.of("order-number", "msp.order_number");

  public void save(MapSetProperty property, String mapSet) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", property.getId());
    ssb.property("map_set", mapSet);
    ssb.property("name", property.getName());
    ssb.property("uri", property.getUri());
    ssb.property("type", property.getType());
    ssb.jsonProperty("description", property.getDescription());
    ssb.property("status", property.getStatus());
    ssb.property("order_number", property.getOrderNumber());
    ssb.property("required", property.isRequired());
    ssb.property("created", property.getCreated());
    ssb.jsonProperty("rule", property.getRule());
    ssb.property("defined_entity_property_id", property.getDefinedEntityPropertyId());

    SqlBuilder sb = ssb.buildSave("terminology.map_set_property", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    property.setId(id);
  }

  public MapSetProperty load(Long id) {
    String sql = "select * from terminology.map_set_property msp where msp.sys_status = 'A' and msp.id = ?";
    return getBean(sql, bp, id);
  }

  public MapSetProperty load(String name, String mapSet) {
    String sql = "select * from terminology.map_set_property msp where msp.sys_status = 'A' and msp.name = ? msp ep.map_set = ?";
    return getBean(sql, bp, name, mapSet);
  }

  public QueryResult<MapSetProperty> query(MapSetPropertyQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.map_set_property msp where msp.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.map_set_property msp where msp.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetPropertyQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getIds())) {
      sb.and().in("msp.id", params.getIds(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getNames())) {
      sb.and().in("msp.name", params.getNames());
    }
    sb.appendIfNotNull("and msp.map_set = ?", params.getMapSet());
    if (CollectionUtils.isNotEmpty(params.getPermittedMapSets())) {
      sb.and().in("msp.map_set", params.getPermittedMapSets());
    }
    return sb;
  }

  public void retain(List<MapSetProperty> properties, String mapSet) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_property set sys_status = 'C'");
    sb.append(" where map_set = ? and sys_status = 'A'", mapSet);
    sb.andNotIn("id", properties, MapSetProperty::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_property set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
