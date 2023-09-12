package com.kodality.termx.terminology.definedproperty;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.property.DefinedProperty;
import com.kodality.termx.ts.property.DefinedPropertyQueryParams;
import io.micronaut.core.util.StringUtils;
import javax.inject.Singleton;

@Singleton
public class DefinedPropertyRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(DefinedProperty.class, p -> {
    p.addColumnProcessor("rule", PgBeanProcessor.fromJson());
    p.addColumnProcessor("description", PgBeanProcessor.fromJson());
  });

  private final static String used = " ( " +
      "exists (select 1 from terminology.entity_property ep where ep.sys_status = 'A' and ep.defined_entity_property_id = dep.id) or " +
      "exists (select 1 from terminology.map_set_property msp where msp.sys_status = 'A' and msp.defined_entity_property_id = dep.id) ) used ";

  public void save(DefinedProperty entityProperty) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entityProperty.getId());
    ssb.property("name", entityProperty.getName());
    ssb.property("kind", entityProperty.getKind());
    ssb.property("type", entityProperty.getType());
    ssb.property("uri", entityProperty.getUri());
    ssb.jsonProperty("rule", entityProperty.getRule());
    ssb.jsonProperty("description", entityProperty.getDescription());

    SqlBuilder sb = ssb.buildSave("terminology.defined_entity_property", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entityProperty.setId(id);
  }

  public DefinedProperty load(Long id) {
    String sql ="select dep.*, " + used + " from terminology.defined_entity_property dep where dep.sys_status = 'A' and dep.id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<DefinedProperty> query(DefinedPropertyQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.defined_entity_property dep where dep.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select dep.*, " + used + " from terminology.defined_entity_property dep where dep.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(DefinedPropertyQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (dep.name ~* ?" +
          "or exists (select 1 from jsonb_each_text(dep.description) where value ~* ?)" +
          ")", params.getTextContains(), params.getTextContains());
    }    return sb;
  }

}
