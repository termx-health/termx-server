package com.kodality.termx.terminology.mapset.association.propertyvalue;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.mapset.MapSetPropertyValue;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class MapSetPropertyValueRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetPropertyValue.class, bp -> bp.addColumnProcessor("value", PgBeanProcessor.fromJson()));

  String select = "select mspv.*, msp.name as map_set_property_name, msp.type as map_set_property_type ";
  String from = " from terminology.map_set_property_value mspv " +
      "inner join terminology.map_set_property msp on msp.id = mspv.map_set_property_id ";

  public void save(MapSetPropertyValue value, Long mapSetAssociationId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", value.getId());
    ssb.property("map_set_property_id", value.getMapSetPropertyId());
    ssb.property("map_set_association_id", mapSetAssociationId);
    ssb.jsonProperty("value", value.getValue());

    SqlBuilder sb = ssb.buildSave("terminology.map_set_property_value", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    value.setId(id);
  }

  public void retain(List<MapSetPropertyValue> values, Long mapSetAssociationId) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_property_value set sys_status = 'C'");
    sb.append(" where map_set_association_id = ? and sys_status = 'A'", mapSetAssociationId);
    sb.andNotIn("id", values, MapSetPropertyValue::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void delete(Long propertyId) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_property_value set sys_status = 'C' where map_set_property_id = ? and sys_status = 'A'", propertyId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void retain(List<Entry<Long, List<MapSetPropertyValue>>> values) {
    String query = "update terminology.map_set_property_value set sys_status = 'C' where map_set_association_id = ? and sys_status = 'A' and id not in " +
        "(select jsonb_array_elements(?::jsonb)::bigint)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setLong(1, values.get(i).getKey());
        ps.setString(2, JsonUtil.toJson(values.get(i).getValue().stream().map(MapSetPropertyValue::getId).filter(Objects::nonNull).toList()));
      }
      @Override
      public int getBatchSize() {
        return values.size();
      }
    });
  }

  public void save(List<Pair<Long, MapSetPropertyValue>> values) {
    List<Pair<Long, MapSetPropertyValue>> valuesToInsert = values.stream().filter(p -> p.getValue().getId() == null).toList();
    String query = "insert into terminology.map_set_property_value (map_set_association_id, map_set_property_id, value) values (?,?,?::jsonb)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        MapSetPropertyValueRepository.this.setValues(ps, i, valuesToInsert);
      }
      @Override public int getBatchSize() {return valuesToInsert.size();}
    });

    List<Pair<Long, MapSetPropertyValue>> valuesToUpdate = values.stream().filter(p -> p.getValue().getId() != null).toList();
    query = "update terminology.entity_property_value SET map_set_association_id = ?, map_set_property_id = ? , value = ?::jsonb where id = ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        MapSetPropertyValueRepository.this.setValues(ps, i, valuesToUpdate);
        ps.setLong(4, valuesToUpdate.get(i).getValue().getId());
      }
      @Override public int getBatchSize() {return valuesToUpdate.size();}
    });
  }

  private void setValues(PreparedStatement ps, int i, List<Pair<Long, MapSetPropertyValue>> values) throws SQLException {
    ps.setLong(1, values.get(i).getKey());
    ps.setLong(2, values.get(i).getValue().getMapSetPropertyId());
    ps.setString(3, JsonUtil.toJson(values.get(i).getValue().getValue()));
  }
}
