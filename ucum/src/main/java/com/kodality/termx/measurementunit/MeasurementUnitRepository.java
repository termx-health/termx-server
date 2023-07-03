package com.kodality.termx.measurementunit;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ucum.MeasurementUnit;
import com.kodality.termx.ucum.MeasurementUnitMapping;
import com.kodality.termx.ucum.MeasurementUnitQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class MeasurementUnitRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MeasurementUnit.class, p -> {
    p.addColumnProcessor("names", PgBeanProcessor.fromJson());
    p.addColumnProcessor("alias", PgBeanProcessor.fromJson());
    p.addColumnProcessor("period", PgBeanProcessor.toLocalDateRange());
    p.addColumnProcessor("mappings", PgBeanProcessor.fromJson(JsonUtil.getListType(MeasurementUnitMapping.class)));
  });

  private String select() {
    return "select mu.*" +
        ", (select jsonb_agg(json_build_object('system', system, 'systemUnit', system_unit, 'systemValue', system_value))" +
        "   from ucum.measurement_unit_mapping mum where mum.measurement_unit_id = mu.id and mum.sys_status = 'A') mappings" +
        " from ucum.measurement_unit mu";
  }

  public QueryResult<MeasurementUnit> query(MeasurementUnitQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from ucum.measurement_unit mu where mu.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select() + " where mu.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  public List<String> loadKinds() {
    SqlBuilder sb = new SqlBuilder("select distinct kind from ucum.measurement_unit mu where mu.sys_status = 'A'");
    return jdbcTemplate.queryForList(sb.getSql(), String.class);
  }

  private SqlBuilder filter(MeasurementUnitQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getCode())) {
      sb.and().in("mu.code", params.getCode());
    }
    if (StringUtils.isNotEmpty(params.getCodeCisEq())) {
      sb.and().in("lower(mu.code)", params.getCodeCisEq().toLowerCase());
    }
    if (StringUtils.isNotEmpty(params.getKind())) {
      sb.and().in("mu.kind", params.getKind());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (");
      sb.append("mu.kind ilike '%' || ? || '%'  ", params.getTextContains());
      sb.or("mu.code ilike '%' || ? || '%'", params.getTextContains());
      sb.or("mu.names::text ilike '%' || ? || '%'", params.getTextContains());
      sb.or("mu.alias::text ilike '%' || ? || '%'", params.getTextContains());
      sb.append(")");
    }
    sb.appendIfNotNull("and mu.period @> ?", params.getDate());
    return sb;
  }

  public MeasurementUnit load(Long id) {
    return getBean(select() + " where mu.id = ?", bp, id);
  }

  public MeasurementUnit load(String code) {
    return getBean(select() + " where mu.sys_status = 'A' and mu.code = ?", bp, code);
  }

  public void save(MeasurementUnit unit) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", unit.getId());
    ssb.property("code", unit.getCode());
    ssb.jsonProperty("names", unit.getNames());
    ssb.jsonProperty("alias", unit.getAlias());
    ssb.property("period", "?::daterange", unit.getPeriod() != null ? unit.getPeriod().asString() : null);
    ssb.property("rounding", unit.getRounding());
    ssb.property("kind", unit.getKind());
    ssb.property("definition_unit", unit.getDefinitionUnit());
    ssb.property("definition_value", unit.getDefinitionValue());

    SqlBuilder sb = ssb.buildSave("ucum.measurement_unit", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    unit.setId(id);
  }
}
