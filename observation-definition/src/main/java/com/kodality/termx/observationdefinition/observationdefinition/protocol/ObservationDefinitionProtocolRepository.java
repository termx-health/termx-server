package com.kodality.termx.observationdefinition.observationdefinition.protocol;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.observationdefintion.ObservationDefinitionProtocol;
import jakarta.inject.Singleton;

@Singleton
public class ObservationDefinitionProtocolRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ObservationDefinitionProtocol.class, p -> {
    p.addColumnProcessor("device", PgBeanProcessor.fromJson());
    p.addColumnProcessor("method", PgBeanProcessor.fromJson());
    p.addColumnProcessor("measurement_location", PgBeanProcessor.fromJson());
    p.addColumnProcessor("specimen", PgBeanProcessor.fromJson());
    p.addColumnProcessor("position", PgBeanProcessor.fromJson());
    p.addColumnProcessor("data_collection_circumstances", PgBeanProcessor.fromJson());
  });


  public void save(ObservationDefinitionProtocol protocol, Long observationDefinitionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", protocol.getId());
    ssb.property("observation_definition_id", observationDefinitionId);
    ssb.jsonProperty("device", protocol.getDevice());
    ssb.jsonProperty("method", protocol.getMethod());
    ssb.jsonProperty("measurement_location", protocol.getMeasurementLocation());
    ssb.jsonProperty("specimen", protocol.getSpecimen());
    ssb.jsonProperty("position", protocol.getPosition());
    ssb.jsonProperty("data_collection_circumstances", protocol.getDataCollectionCircumstances());
    SqlBuilder sb = ssb.buildSave("def.observation_definition_protocol", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    protocol.setId(id);
  }

  public ObservationDefinitionProtocol load(Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("select * from def.observation_definition_protocol where observation_definition_id = ? and sys_status = 'A'", observationDefinitionId);
    return getBean(sb.getSql(), bp, sb.getParams());
  }
}
