package com.kodality.termx.terminology.terminology.codesystem.entityproperty;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class EntityPropertyRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(EntityProperty.class, p -> {
    p.addColumnProcessor("rule", PgBeanProcessor.fromJson());
    p.addColumnProcessor("description", PgBeanProcessor.fromJson());
  });

  private final Map<String, String> orderMapping = Map.of("order-number", "ep.order_number");


  public void save(EntityProperty entityProperty, String codeSystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entityProperty.getId());
    ssb.property("code_system", codeSystem);
    ssb.property("name", entityProperty.getName());
    ssb.property("uri", entityProperty.getUri());
    ssb.property("kind", entityProperty.getKind());
    ssb.property("type", entityProperty.getType());
    ssb.jsonProperty("description", entityProperty.getDescription());
    ssb.property("status", entityProperty.getStatus());
    ssb.property("order_number", entityProperty.getOrderNumber());
    ssb.property("preferred", entityProperty.isPreferred());
    ssb.property("required", entityProperty.isRequired());
    ssb.property("show_in_list", entityProperty.isShowInList());
    ssb.property("created", entityProperty.getCreated());
    ssb.jsonProperty("rule", entityProperty.getRule());
    ssb.property("defined_entity_property_id", entityProperty.getDefinedEntityPropertyId());

    SqlBuilder sb = ssb.buildSave("terminology.entity_property", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entityProperty.setId(id);
  }

  public EntityProperty load(Long id) {
    String sql ="select * from terminology.entity_property ep where ep.sys_status = 'A' and ep.id = ?";
    return getBean(sql, bp, id);
  }

  public EntityProperty load(String codeSystem, String name) {
    String sql ="select * from terminology.entity_property ep where ep.sys_status = 'A' and ep.code_system = ? and ep.name = ?";
    return getBean(sql, bp, codeSystem, name);
  }

  public QueryResult<EntityProperty> query(EntityPropertyQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.entity_property ep where ep.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.entity_property ep where ep.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(EntityPropertyQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.and().in("ep.code_system", params.getPermittedCodeSystems());
    if (StringUtils.isNotEmpty(params.getIds())) {
      sb.and().in("ep.id", params.getIds(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getNames())) {
      sb.and().in("ep.name", params.getNames());
    }
    sb.and().in("ep.code_system", params.getCodeSystem());
    return sb;
  }

  public void retain(String codeSystem, List<EntityProperty> properties) {
    SqlBuilder sb = new SqlBuilder("update terminology.entity_property set sys_status = 'C'");
    sb.append(" where code_system = ? and sys_status = 'A'", codeSystem);
    sb.andNotIn("id", properties, EntityProperty::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void cancel(String codeSystem, Long id) {
    String sql = "update terminology.entity_property set sys_status = 'C' where code_system = ? and id = ? and sys_status = 'A'";
    jdbcTemplate.update(sql, codeSystem, id);
  }
}
