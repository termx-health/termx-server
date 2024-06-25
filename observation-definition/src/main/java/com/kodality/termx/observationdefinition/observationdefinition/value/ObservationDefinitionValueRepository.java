package com.kodality.termx.observationdefinition.observationdefinition.value;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.observationdefintion.ObservationDefinitionValue;
import jakarta.inject.Singleton;

@Singleton
public class ObservationDefinitionValueRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ObservationDefinitionValue.class, p -> {
    p.addColumnProcessor("unit", PgBeanProcessor.fromJson());
    p.addColumnProcessor("values", PgBeanProcessor.fromJson());
  });


  public void save(ObservationDefinitionValue value, Long observationDefinitionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", value.getId());
    ssb.property("observation_definition_id", observationDefinitionId);
    ssb.property("behaviour", value.getBehaviour());
    ssb.property("expression", value.getExpression());
    ssb.property("type", value.getType());
    ssb.jsonProperty("unit", value.getUnit());
    ssb.property("usage", value.getUsage());
    ssb.jsonProperty("values", value.getValues());
    ssb.property("value_set", value.getValueSet());
    ssb.property("multiple_results_allowed", value.isMultipleResultsAllowed());
    SqlBuilder sb = ssb.buildSave("def.observation_definition_value", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    value.setId(id);
  }

  public void cancel(Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("update def.observation_definition_value set sys_status = 'C' where observation_definition_id = ? and sys_status = 'A'", observationDefinitionId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public ObservationDefinitionValue load(Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("select * from def.observation_definition_value where observation_definition_id = ? and sys_status = 'A'", observationDefinitionId);
    return getBean(sb.getSql(), bp, sb.getParams());
  }
}
