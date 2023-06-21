package com.kodality.termserver.terminology.codesystem.entityproperty;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyQueryParams;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class EntityPropertyRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(EntityProperty.class, p -> {
    p.addColumnProcessor("rule", PgBeanProcessor.fromJson());
  });

  private final Map<String, String> orderMapping = Map.of("order-number", "ep.order_number");

  String from = " from terminology.entity_property ep left join terminology.code_system_supplement css on css.target_id = ep.id and css.target_type = 'EntityProperty' ";

  public void save(EntityProperty entityProperty, String codeSystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entityProperty.getId());
    ssb.property("code_system", codeSystem);
    ssb.property("name", entityProperty.getName());
    ssb.property("type", entityProperty.getType());
    ssb.property("description", entityProperty.getDescription());
    ssb.property("status", entityProperty.getStatus());
    ssb.property("order_number", entityProperty.getOrderNumber());
    ssb.property("preferred", entityProperty.isPreferred());
    ssb.property("required", entityProperty.isRequired());
    ssb.property("created", entityProperty.getCreated());
    ssb.jsonProperty("rule", entityProperty.getRule());

    SqlBuilder sb = ssb.buildSave("terminology.entity_property", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entityProperty.setId(id);
  }

  public EntityProperty load(Long id) {
    String sql ="select ep.*, css.id supplement_id" + from + "where ep.sys_status = 'A' and ep.id = ?";
    return getBean(sql, bp, id);
  }

  public EntityProperty load(String name, String codeSystem) {
    String sql ="select ep.*, css.id supplement_id" + from + "where ep.sys_status = 'A' and ep.name = ? and ep.code_system = ?";
    return getBean(sql, bp, name, codeSystem);
  }

  public QueryResult<EntityProperty> query(EntityPropertyQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1)" + from + "where ep.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select ep.*, css.id supplement_id" + from + "where ep.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(EntityPropertyQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getIds())) {
      sb.and().in("ep.id", params.getIds(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getNames())) {
      sb.and().in("ep.name", params.getNames());
    }
    sb.appendIfNotNull("and ep.code_system = ?", params.getCodeSystem());
    if (CollectionUtils.isNotEmpty(params.getPermittedCodeSystems())) {
      sb.and().in("ep.code_system", params.getPermittedCodeSystems());
    }
    return sb;
  }

  public void retain(List<EntityProperty> properties, String codeSystem) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property set sys_status = 'C'");
    sb.append(" where code_system = ? and sys_status = 'A'", codeSystem);
    sb.andNotIn("id", properties, EntityProperty::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
