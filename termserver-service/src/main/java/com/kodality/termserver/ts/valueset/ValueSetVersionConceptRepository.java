package com.kodality.termserver.ts.valueset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.valueset.ValueSetVersion.ValueSetConcept;
import io.micronaut.core.util.CollectionUtils;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class ValueSetVersionConceptRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetConcept.class, bp -> {
    bp.addColumnProcessor("display", "display", (rs, index, propType) -> new Designation().setId(rs.getLong("display")));
    bp.addColumnProcessor("concept_id", "concept", (rs, index, propType) -> new Concept().setId(rs.getLong("concept_id")));
    bp.addRowProcessor("additional_designations", rs -> {
      Array designationIds = rs.getArray("additional_designations");
      return designationIds == null ? null :
          Arrays.stream((Long[]) designationIds.getArray()).map(id -> new Designation().setId(id)).collect(Collectors.toList());
    });
  });

  public List<ValueSetConcept> getConcepts(Long valueSetVersionId) {
    String sql = "select * from concept_value_set_version_membership where sys_status = 'A' and value_set_version_id = ? ";
    return getBeans(sql, bp, valueSetVersionId);
  }

  public void retainConcepts(List<ValueSetConcept> concepts, Long valueSetVersionId) {
    SqlBuilder sb = new SqlBuilder("update concept_value_set_version_membership set sys_status = 'C'");
    sb.append(" where value_set_version_id = ? and sys_status = 'A'", valueSetVersionId);
    sb.andNotIn("id", concepts, ValueSetConcept::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void upsertConcepts(List<ValueSetConcept> concepts, Long valueSetVersionId) {
    if (concepts == null) {
      return;
    }
    concepts.forEach(c -> {
      SaveSqlBuilder ssb = new SaveSqlBuilder();
      ssb.property("id", c.getId());
      ssb.property("concept_id", c.getConcept().getId());
      ssb.property("order_nr", c.getOrderNr());
      ssb.property("display", c.getDisplay() == null ? null : c.getDisplay().getId());
      ssb.property("additional_designations", "?::bigint[]", CollectionUtils.isEmpty(c.getAdditionalDesignations()) ? null :
          PgUtil.array(c.getAdditionalDesignations().stream().map(Designation::getId).collect(Collectors.toList())));
      ssb.property("value_set_version_id", valueSetVersionId);
      SqlBuilder sb = ssb.buildSave("concept_value_set_version_membership", "id");
      Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
      c.setId(id);
    });
  }
}
