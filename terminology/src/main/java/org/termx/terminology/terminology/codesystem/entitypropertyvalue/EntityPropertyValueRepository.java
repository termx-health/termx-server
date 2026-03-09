package org.termx.terminology.terminology.codesystem.entitypropertyvalue;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import org.termx.ts.codesystem.EntityPropertyValue;
import org.termx.ts.codesystem.EntityPropertyValueQueryParams;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Stream;

@Singleton
public class EntityPropertyValueRepository extends BaseRepository {
  private static final int INSERT_CHUNK_SIZE = 500;
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

  public List<EntityPropertyValue> loadAll(Long codeSystemEntityVersionId, Long baseEntityVersionId) {
    SqlBuilder sb = new SqlBuilder();
    sb.append(select);
    sb.appendIfNotNull(", epv.code_system_entity_version_id = ? as supplement", baseEntityVersionId);
    sb.append( from + "where epv.sys_status = 'A'");
    sb.and().in("epv.code_system_entity_version_id", Stream.of(codeSystemEntityVersionId, baseEntityVersionId).filter(Objects::nonNull).toList());
    sb.append("order by ep.order_number");
    return getBeans(sb.getSql(), bp, sb.getParams());
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
      sb.append("order by ep.order_number");
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
    for (List<Pair<Long, EntityPropertyValue>> chunk : ListUtils.partition(valuesToInsert, INSERT_CHUNK_SIZE)) {
      SqlBuilder sb = new SqlBuilder("insert into terminology.entity_property_value (code_system_entity_version_id, entity_property_id, value) values ");
      for (Pair<Long, EntityPropertyValue> pair : chunk) {
        sb.append("(?, ?, ?::jsonb),", pair.getKey(), pair.getValue().getEntityPropertyId(), JsonUtil.toJson(pair.getValue().getValue()));
      }
      String sql = sb.getSql();
      sql = sql.substring(0, sql.length() - 1) + " returning id";
      List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, sb.getParams());
      for (int i = 0; i < chunk.size(); i++) {
        chunk.get(i).getValue().setId(ids.get(i));
      }
    }

    List<Pair<Long, EntityPropertyValue>> valuesToUpdate = values.stream().filter(p -> p.getValue().getId() != null).toList();
    String query = "update terminology.entity_property_value SET code_system_entity_version_id = ?, entity_property_id = ? , value = ?::jsonb where id = ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        EntityPropertyValueRepository.this.setValues(ps, i, valuesToUpdate);
        ps.setLong(4, valuesToUpdate.get(i).getValue().getId());
      }
      @Override public int getBatchSize() {return valuesToUpdate.size();}
    });
  }

  public List<EntityPropertyValue> loadCodingValuesByCodeSystemEntityVersionId(Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder(select + from + "where epv.sys_status = 'A'");
    sb.append("and epv.code_system_entity_version_id = ?", codeSystemEntityVersionId);
    sb.append("and ep.type = 'Coding'");
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public List<EntityPropertyValue> loadCodingValuesByCodeSystemEntityVersionIds(List<Long> codeSystemEntityVersionIds) {
    if (codeSystemEntityVersionIds == null || codeSystemEntityVersionIds.isEmpty()) {
      return List.of();
    }
    SqlBuilder sb = new SqlBuilder(select + from + "where epv.sys_status = 'A'");
    sb.and().in("epv.code_system_entity_version_id", codeSystemEntityVersionIds);
    sb.append("and ep.type = 'Coding'");
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void updateValues(List<EntityPropertyValue> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    String sql = "update terminology.entity_property_value set value = ?::jsonb where id = ? and sys_status = 'A'";
    jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setString(1, JsonUtil.toJson(values.get(i).getValue()));
        ps.setLong(2, values.get(i).getId());
      }

      @Override
      public int getBatchSize() {
        return values.size();
      }
    });
  }

  private void setValues(PreparedStatement ps, int i, List<Pair<Long, EntityPropertyValue>> values) throws SQLException {
    ps.setLong(1, values.get(i).getKey());
    ps.setLong(2, values.get(i).getValue().getEntityPropertyId());
    ps.setString(3, JsonUtil.toJson(values.get(i).getValue().getValue()));
  }

}
