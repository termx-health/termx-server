package com.kodality.termx.observationdefinition.observationdefinition.interpretation;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.observationdefintion.ObservationDefinitionInterpretation;
import com.kodality.termx.observationdefintion.ObservationDefinitionInterpretation.ObservationDefinitionInterpretationTarget;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ObservationDefinitionInterpretationRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ObservationDefinitionInterpretation.class, p -> {
    p.addRowProcessor("target", (rs) -> new ObservationDefinitionInterpretationTarget()
        .setType(rs.getString("target_type"))
        .setId(rs.getLong("target_id")));
    p.addColumnProcessor("state", PgBeanProcessor.fromJson());
    p.addColumnProcessor("range", PgBeanProcessor.fromJson());
  });


  public void save(ObservationDefinitionInterpretation interpretation, Long observationDefinitionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", interpretation.getId());
    ssb.property("observation_definition_id", observationDefinitionId);
    ssb.property("target_type", interpretation.getTarget().getType());
    ssb.property("target_id", interpretation.getTarget().getId());
    ssb.property("order_number", interpretation.getOrderNumber());
    ssb.jsonProperty("state", interpretation.getState());
    ssb.jsonProperty("range", interpretation.getRange());
    ssb.property("condition", interpretation.getCondition());
    ssb.property("range_category", interpretation.getRangeCategory());
    ssb.property("fhir_interpretation_code", interpretation.getFhirInterpretationCode());
    ssb.property("snomed_interpretation_code", interpretation.getSnomedInterpretationCode());
    SqlBuilder sb = ssb.buildSave("def.observation_definition_interpretation", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    interpretation.setId(id);
  }

  public List<ObservationDefinitionInterpretation> load(Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("select * from def.observation_definition_interpretation where observation_definition_id = ? and sys_status = 'A' order by order_number", observationDefinitionId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void retain(List<ObservationDefinitionInterpretation> interpretations, Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("update def.observation_definition_interpretation set sys_status = 'C'");
    sb.append(" where observation_definition_id = ? and sys_status = 'A'", observationDefinitionId);
    sb.andNotIn("id", interpretations, ObservationDefinitionInterpretation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
