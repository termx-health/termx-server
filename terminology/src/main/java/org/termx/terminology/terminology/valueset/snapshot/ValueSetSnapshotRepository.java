package org.termx.terminology.terminology.valueset.snapshot;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import org.termx.ts.valueset.ValueSetSnapshot;
import org.termx.ts.valueset.ValueSetSnapshotDependency;
import org.termx.ts.valueset.ValueSetVersionConcept;
import jakarta.inject.Singleton;

@Singleton
public class ValueSetSnapshotRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetSnapshot.class, bp -> {
    bp.addColumnProcessor("expansion", PgBeanProcessor.fromJson(JsonUtil.getListType(ValueSetVersionConcept.class)));
    bp.addColumnProcessor("dependencies", PgBeanProcessor.fromJson(JsonUtil.getListType(ValueSetSnapshotDependency.class)));
    bp.addColumnProcessor("value_set_version", PgBeanProcessor.fromJson());
  });

  private final static String select = "select vss.*, " +
      "(select json_build_object('id', vsv.id, 'version', vsv.version) from terminology.value_set_version vsv where vsv.id = vss.value_set_version_id and vsv.sys_status = 'A') as value_set_version ";

  public void save(ValueSetSnapshot snapshot) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", snapshot.getId());
    ssb.property("value_set", snapshot.getValueSet());
    ssb.property("value_set_version_id", snapshot.getValueSetVersion().getId());
    ssb.property("concepts_total", snapshot.getConceptsTotal());
    ssb.jsonProperty("expansion", snapshot.getExpansion(), false);
    ssb.jsonProperty("dependencies", snapshot.getDependencies(), false);
    ssb.property("created_at", snapshot.getCreatedAt());
    ssb.property("created_by", snapshot.getCreatedBy());
    SqlBuilder sb = ssb.buildSave("terminology.value_set_snapshot", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    snapshot.setId(id);
  }

  public ValueSetSnapshot load(String valueSet, Long valueSetVersionId) {
    String sql = select + "from terminology.value_set_snapshot vss where vss.sys_status = 'A' and vss.value_set = ? and vss.value_set_version_id = ?";
    return getBean(sql, bp, valueSet, valueSetVersionId);
  }

  /**
   * Returns ONLY {@code concepts_total} for the saved snapshot, without loading the
   * (potentially huge) {@code expansion} jsonb column. Lets the FHIR read flow emit
   * {@code expansion.total} on {@code ?_summary=false} without paying for the full
   * expansion list — used by {@link ValueSetResourceStorage} to populate the count
   * on the open-in-FHIR list link. Returns {@code null} when no snapshot exists.
   */
  public Integer loadConceptsTotal(String valueSet, Long valueSetVersionId) {
    String sql = "select concepts_total from terminology.value_set_snapshot " +
        "where sys_status = 'A' and value_set = ? and value_set_version_id = ? limit 1";
    return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getInt(1) : null, valueSet, valueSetVersionId);
  }

}
