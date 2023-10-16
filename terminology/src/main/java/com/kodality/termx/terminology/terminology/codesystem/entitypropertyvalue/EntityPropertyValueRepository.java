package com.kodality.termx.terminology.terminology.codesystem.entitypropertyvalue;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.codesystem.EntityPropertyValueQueryParams;
import io.micronaut.core.util.StringUtils;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class EntityPropertyValueRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(EntityPropertyValue.class, bp -> bp.addColumnProcessor("value", PgBeanProcessor.fromJson()));

  String select = "select epv.*, ep.name as entity_property, ep.type as entity_property_type ";
  String from = " from terminology.entity_property_value epv " +
      "inner join terminology.entity_property ep on ep.id = epv.entity_property_id ";

  public void save(EntityPropertyValue value, Long codeSystemEntityVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", value.getId());
    ssb.property("entity_property_id", value.getEntityPropertyId());
    ssb.property("code_system_entity_version_id", codeSystemEntityVersionId);
    ssb.jsonProperty("value", value.getValue());

    SqlBuilder sb = ssb.buildSave("terminology.entity_property_value", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    value.setId(id);
  }

  public List<EntityPropertyValue> loadAll(Long codeSystemEntityVersionId) {
    String sql = select + from + "where epv.sys_status = 'A' and epv.code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public EntityPropertyValue load(Long id) {
    String sql = select + from + "where epv.sys_status = 'A' and epv.id = ?";
    return getBean(sql, bp, id);
  }

  public void retain(List<EntityPropertyValue> values, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property_value set sys_status = 'C'");
    sb.append(" where code_system_entity_version_id = ? and sys_status = 'A'", codeSystemEntityVersionId);
    sb.andNotIn("id", values, EntityPropertyValue::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void delete(Long propertyId) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property_value set sys_status = 'C' where entity_property_id = ? and sys_status = 'A'", propertyId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public QueryResult<EntityPropertyValue> query(EntityPropertyValueQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1)" + from + "where epv.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + from + "where epv.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(EntityPropertyValueQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getCodeSystemEntityVersionId())) {
      sb.and().in("epv.code_system_entity_version_id", params.getCodeSystemEntityVersionId(), Long::valueOf);
    }
    sb.appendIfNotNull("and epv.entity_property_id = ?", params.getPropertyId());
   return sb;
  }

  public void retain(List<Entry<Long, List<EntityPropertyValue>>> values) {
    String query = "update terminology.entity_property_value set sys_status = 'C' where code_system_entity_version_id = ? and sys_status = 'A' and id not in " +
        "(select jsonb_array_elements(?::jsonb)::bigint)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setLong(1, values.get(i).getKey());
        ps.setString(2, JsonUtil.toJson(values.get(i).getValue().stream().map(EntityPropertyValue::getId).filter(Objects::nonNull).toList()));
      }
      @Override
      public int getBatchSize() {
        return values.size();
      }
    });
  }

  public void save(List<Pair<Long, EntityPropertyValue>> values) {
    List<Pair<Long, EntityPropertyValue>> valuesToInsert = values.stream().filter(p -> p.getValue().getId() == null).toList();
    String query = "insert into terminology.entity_property_value (code_system_entity_version_id, entity_property_id, value) values (?,?,?::jsonb)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        EntityPropertyValueRepository.this.setValues(ps, i, valuesToInsert);
      }
      @Override public int getBatchSize() {return valuesToInsert.size();}
    });

    List<Pair<Long, EntityPropertyValue>> valuesToUpdate = values.stream().filter(p -> p.getValue().getId() != null).toList();
    query = "update terminology.entity_property_value SET code_system_entity_version_id = ?, entity_property_id = ? , value = ?::jsonb where id = ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        EntityPropertyValueRepository.this.setValues(ps, i, valuesToUpdate);
        ps.setLong(4, valuesToUpdate.get(i).getValue().getId());
      }
      @Override public int getBatchSize() {return valuesToUpdate.size();}
    });
  }

  private void setValues(PreparedStatement ps, int i, List<Pair<Long, EntityPropertyValue>> values) throws SQLException {
    ps.setLong(1, values.get(i).getKey());
    ps.setLong(2, values.get(i).getValue().getEntityPropertyId());
    ps.setString(3, JsonUtil.toJson(values.get(i).getValue().getValue()));
  }

}
