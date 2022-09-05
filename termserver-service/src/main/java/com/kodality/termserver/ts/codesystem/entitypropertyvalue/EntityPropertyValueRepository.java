package com.kodality.termserver.ts.codesystem.entitypropertyvalue;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.codesystem.EntityPropertyValueQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class EntityPropertyValueRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(EntityPropertyValue.class, bp -> {
    bp.addColumnProcessor("value", PgBeanProcessor.fromJson());
  });

  String from = " from terminology.entity_property_value epv left join terminology.code_system_supplement css on css.target_id = epv.id and css.target_type = 'EntityPropertyValue' ";

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
    String sql = "select epv.*, css.id supplement_id" + from + "where epv.sys_status = 'A' and epv.code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public EntityPropertyValue load(Long id) {
    String sql = "select epv.*, css.id supplement_id" + from + "where epv.sys_status = 'A' and epv.id = ?";
    return getBean(sql, bp, id);
  }

  public void retain(List<EntityPropertyValue> values, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property_value set sys_status = 'C'");
    sb.append(" where code_system_entity_version_id = ? and sys_status = 'A'", codeSystemEntityVersionId);
    sb.andNotIn("id", values, EntityPropertyValue::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property_value set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public QueryResult<EntityPropertyValue> query(EntityPropertyValueQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1)" + from + "where epv.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select epv.*, css.id supplement_id" + from + "where epv.sys_status = 'A'");
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

}
