package com.kodality.termserver.ts.codesystem.entityproperty;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import jakarta.inject.Singleton;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class EntityPropertyRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(EntityProperty.class);

  public void save(EntityProperty entityProperty, String codeSystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entityProperty.getId());
    ssb.property("code_system", codeSystem);
    ssb.property("name", entityProperty.getName());
    ssb.property("type", entityProperty.getType());
    ssb.property("description", entityProperty.getDescription());
    ssb.property("status", entityProperty.getStatus());
    ssb.property("created", entityProperty.getCreated());

    SqlBuilder sb = ssb.buildSave("terminology.entity_property", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entityProperty.setId(id);
  }

  public List<EntityProperty> getProperties(String codeSystem) {
    String sql = "select * from terminology.entity_property where sys_status = 'A' and code_system = ?";
    return getBeans(sql, bp, codeSystem);
  }

  public QueryResult<EntityProperty> query(EntityPropertyQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.entity_property ep where ep.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.entity_property ep where ep.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(EntityPropertyQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getNames())) {
      sb.and().in("ep.name", params.getNames());
    }
    sb.appendIfNotNull("and ep.code_system = ?", params.getCodeSystem());
    sb.appendIfNotNull("and exists(select 1 from terminology.value_set vs " +
        "inner join terminology.value_set_version vsv on vsv.value_set = vs.id and vsv.sys_status = 'A' " +
        "inner join terminology.concept_value_set_version_membership cvsvm on cvsvm.value_set_version_id = vsv.id and cvsvm.sys_status = 'A' " +
        "inner join terminology.concept c on c.id = cvsvm.concept_id and c.sys_status = 'A' " +
        "where c.code_system = ep.code_system and vs.id = ? and vs.sys_status = 'A')", params.getValueSet());
    return sb;
  }

  public void retain(List<EntityProperty> properties, String codeSystem) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property set sys_status = 'C'");
    sb.append(" where code_system = ? and sys_status = 'A'", codeSystem);
    sb.andNotIn("id", properties, EntityProperty::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
