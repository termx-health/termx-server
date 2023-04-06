package com.kodality.termserver.observationdefinition.mapping;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.observationdefintion.ObservationDefinitionMapping;
import com.kodality.termserver.observationdefintion.ObservationDefinitionMapping.ObservationDefinitionMappingTarget;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ObservationDefinitionMappingRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ObservationDefinitionMapping.class, p -> {
    p.addRowProcessor("target", (rs) -> new ObservationDefinitionMappingTarget()
        .setType(rs.getString("target_type"))
        .setId(rs.getLong("target_id")));
  });


  public void save(ObservationDefinitionMapping mapping, Long observationDefinitionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", mapping.getId());
    ssb.property("observation_definition_id", observationDefinitionId);
    ssb.property("target_type", mapping.getTarget().getType());
    ssb.property("target_id", mapping.getTarget().getId());
    ssb.property("order_number", mapping.getOrderNumber());
    ssb.property("map_set", mapping.getMapSet());
    ssb.property("code_system", mapping.getCodeSystem());
    ssb.property("concept", mapping.getConcept());
    ssb.property("relation", mapping.getRelation());
    ssb.property("condition", mapping.getCondition());
    SqlBuilder sb = ssb.buildSave("def.observation_definition_mapping", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    mapping.setId(id);
  }

  public List<ObservationDefinitionMapping> load(Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("select * from def.observation_definition_mapping where observation_definition_id = ? and sys_status = 'A' order by order_number", observationDefinitionId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void retain(List<ObservationDefinitionMapping> mappings, Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("update def.observation_definition_mapping set sys_status = 'C'");
    sb.append(" where observation_definition_id = ? and sys_status = 'A'", observationDefinitionId);
    sb.andNotIn("id", mappings, ObservationDefinitionMapping::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
