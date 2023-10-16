package com.kodality.termx.ucum.measurementunit.mapping;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.ucum.MeasurementUnitMapping;
import java.util.List;
import javax.inject.Singleton;

import static java.util.stream.Collectors.joining;

@Singleton
public class MeasurementUnitMappingRepository extends BaseRepository {

  public void save(Long measurementUnitId, List<MeasurementUnitMapping> mappings) {
    if (mappings == null || mappings.isEmpty()) {
      String sql = "update ucum.measurement_unit_mapping set sys_status = 'C' where sys_status = 'A' and measurement_unit_id = ?";
      jdbcTemplate.update(sql, measurementUnitId);
      return;
    }
    SqlBuilder sb = new SqlBuilder();
    sb.append("with t(system, system_unit, system_value) as (values ");
    sb.append(mappings.stream().map(m -> "(?,?,?)").collect(joining(",")));
    mappings.forEach(m -> sb.add(m.getSystem(), m.getSystemUnit(), m.getSystemValue()));
    sb.append(")");
    sb.append(", deleted as (update ucum.measurement_unit_mapping mum set sys_status = 'C' where sys_status = 'A' and measurement_unit_id = ?"
        + " and not exists (select 1 from t where t.system = mum.system and t.system_unit = mum.system_unit and mum.sys_status = 'A'))",
              measurementUnitId);
    sb.append(", updated as (update ucum.measurement_unit_mapping mum set system_value = t.system_value from t"
        + "  where mum.system = t.system and mum.system_unit = t.system_unit and sys_status = 'A' and measurement_unit_id = ?)",
              measurementUnitId);
    sb.append(", inserted as (insert into ucum.measurement_unit_mapping (measurement_unit_id, system, system_unit, system_value) "
        + " select ?, t.system, t.system_unit, t.system_value from t where not exists ("
        + " select 1 from ucum.measurement_unit_mapping mum2 where t.system = mum2.system and t.system_unit = mum2.system_unit and mum2.sys_status = 'A'))",
              measurementUnitId);
    sb.append(" select 1");

    jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }
}
