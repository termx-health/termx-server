package com.kodality.termserver.codesystem.entitypropertyvalue;

import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.repo.BaseRepository;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class EntityPropertyValueRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(EntityPropertyValue.class);

  public List<EntityPropertyValue> loadAll(Long codeSystemEntityVersionId) {
    String sql = "select * from entity_property_value where sys_status = 'A' and code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public void retain(List<EntityPropertyValue> values, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update entity_property_value set sys_status = 'C'");
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
          upsert(new SqlBuilder("insert into entity_property_value (" +
                  "code_system_entity_version_id, " +
                  "entity_property_id, " +
                  "value) select ?,?,?",
                  codeSystemEntityVersionId,
                  v.getEntityPropertyId(),
                  v.getValue()
              ),
              new SqlBuilder(
                  "UPDATE entity_property_value SET " +
                      "entity_property_id = ?, " +
                      "value = ? where code_system_entity_version_id = ? and id = ? and sys_status = 'A'",
                  v.getEntityPropertyId(),
                  v.getValue(),
                  codeSystemEntityVersionId,
                  v.getId()));
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }
}
