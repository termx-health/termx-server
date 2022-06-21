package com.kodality.termserver.ts.codesystem.entitypropertyvalue;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class EntityPropertyValueRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(EntityPropertyValue.class, bp -> {
    bp.addColumnProcessor("value", PgBeanProcessor.fromJson());
  });

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
    String sql = "select * from terminology.entity_property_value where sys_status = 'A' and code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public void retain(List<EntityPropertyValue> values, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property_value set sys_status = 'C'");
    sb.append(" where code_system_entity_version_id = ? and sys_status = 'A'", codeSystemEntityVersionId);
    sb.andNotIn("id", values, EntityPropertyValue::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void batchUpsert(List<EntityPropertyValue> values, Long codeSystemEntityVersionId) {
    if (values == null) {
      return;
    }
    values.forEach(v -> {
      SqlBuilder sb =
          upsert(new SqlBuilder("insert into terminology.entity_property_value (" +
                  "code_system_entity_version_id, " +
                  "entity_property_id, " +
                  "value) select ?,?,?::jsonb",
                  codeSystemEntityVersionId,
                  v.getEntityPropertyId(),
                  JsonUtil.toJson(v.getValue())
              ),
              new SqlBuilder(
                  "UPDATE terminology.entity_property_value SET " +
                      "entity_property_id = ?, " +
                      "value = ?::jsonb where code_system_entity_version_id = ? and id = ? and sys_status = 'A'",
                  v.getEntityPropertyId(),
                  JsonUtil.toJson(v.getValue()),
                  codeSystemEntityVersionId,
                  v.getId()));
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }
}
