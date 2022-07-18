package com.kodality.termserver.ts.measurementunit;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.measurementunit.MeasurementUnit;
import com.kodality.termserver.measurementunit.MeasurementUnitMapping;
import com.kodality.termserver.measurementunit.MeasurementUnitSearchParams;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class MeasurementUnitRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MeasurementUnit.class, p -> {
    p.addColumnProcessor("names", PgBeanProcessor.fromJson());
    p.addColumnProcessor("alias", PgBeanProcessor.fromJson());
    p.addColumnProcessor("period", PgBeanProcessor.toLocalDateRange());
    p.addColumnProcessor("kind", PgBeanProcessor.toCodeName());
    p.addColumnProcessor("mappings", PgBeanProcessor.fromJson(JsonUtil.getListType(MeasurementUnitMapping.class)));
  });

  private String select() {
    return "select mu.*" +
        ", (select jsonb_agg(json_build_object('system', system, 'systemUnit', system_unit, 'systemValue', system_value))" +
        "   from terminology.measurement_unit_mapping mum where mum.measurement_unit_id = mu.id and mum.sys_status = 'A') mappings" +
        " from terminology.measurement_unit mu";
  }

  public List<MeasurementUnit> query(MeasurementUnitSearchParams params) {
    SqlBuilder sb = new SqlBuilder(select() + " where mu.sys_status = 'A'");
    sb.appendIfNotNull("and mu.kind = ?", params.getKind());
    return getBeans(sb.getSql(), bp, sb.getParams());
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
    ssb.property("kind", unit.getKind() != null ? unit.getKind().getCode() : null);
    ssb.property("definition_unit", unit.getDefinitionUnit());
    ssb.property("definition_value", unit.getDefinitionValue());

    SqlBuilder sb = ssb.buildSave("terminology.measurement_unit", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    unit.setId(id);
  }
}
