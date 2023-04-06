package com.kodality.termserver.observationdefinition.component;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.observationdefintion.ObservationDefinitionComponent;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ObservationDefinitionComponentRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ObservationDefinitionComponent.class, p -> {
    p.addColumnProcessor("names", PgBeanProcessor.fromJson());
    p.addColumnProcessor("cardinality", PgBeanProcessor.fromJson());
    p.addColumnProcessor("unit", PgBeanProcessor.fromJson());
  });


  public void save(ObservationDefinitionComponent component, Long observationDefinitionId, String type) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", component.getId());
    ssb.property("observation_definition_id", observationDefinitionId);
    ssb.property("section_type", type);
    ssb.property("code", component.getCode());
    ssb.jsonProperty("names", component.getNames());
    ssb.property("order_number", component.getOrderNumber());
    ssb.jsonProperty("cardinality", component.getCardinality());
    ssb.property("type", component.getType());
    ssb.jsonProperty("unit", component.getUnit());
    ssb.property("value_set", component.getValueSet());
    SqlBuilder sb = ssb.buildSave("def.observation_definition_component", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    component.setId(id);
  }

  public List<ObservationDefinitionComponent> load(Long observationDefinitionId, String type) {
    SqlBuilder sb = new SqlBuilder("select * from def.observation_definition_component where observation_definition_id = ? and section_type = ? and sys_status = 'A' order by order_number", observationDefinitionId, type);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void retain(List<ObservationDefinitionComponent> components, Long observationDefinitionId, String type) {
    SqlBuilder sb = new SqlBuilder("update def.observation_definition_component set sys_status = 'C'");
    sb.append(" where observation_definition_id = ? and section_type = ? and sys_status = 'A'", observationDefinitionId, type);
    sb.andNotIn("id", components, ObservationDefinitionComponent::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
