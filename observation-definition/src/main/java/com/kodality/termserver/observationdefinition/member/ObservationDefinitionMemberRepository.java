package com.kodality.termserver.observationdefinition.member;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.observationdefintion.ObservationDefinitionMember;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ObservationDefinitionMemberRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ObservationDefinitionMember.class, p -> {
    p.addColumnProcessor("item_id", "item", PgBeanProcessor.toIdCodeName());
    p.addColumnProcessor("cardinality", PgBeanProcessor.fromJson());
  });


  public void save(ObservationDefinitionMember member, Long observationDefinitionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", member.getId());
    ssb.property("observation_definition_id", observationDefinitionId);
    ssb.property("item_id", member.getItem().getId());
    ssb.property("order_number", member.getOrderNumber());
    ssb.jsonProperty("cardinality", member.getCardinality());
    SqlBuilder sb = ssb.buildSave("def.observation_definition_member", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    member.setId(id);
  }

  public List<ObservationDefinitionMember> load(Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("select * from def.observation_definition_member where observation_definition_id = ? and sys_status = 'A' order by order_number", observationDefinitionId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void retain(List<ObservationDefinitionMember> members, Long observationDefinitionId) {
    SqlBuilder sb = new SqlBuilder("update def.observation_definition_member set sys_status = 'C'");
    sb.append(" where observation_definition_id = ? and sys_status = 'A'", observationDefinitionId);
    sb.andNotIn("id", members, ObservationDefinitionMember::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
