package org.termx.terminology.terminology.valueset.snapshot;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import java.time.OffsetDateTime;
import org.termx.ts.valueset.ValueSetSnapshot;
import org.termx.ts.valueset.ValueSetSnapshotDependency;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionReference;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ValueSetSnapshotRepository extends BaseRepository {

  // Safety valve below the ~1 GB Postgres field limit: if even the compressed expansion is larger,
  // don't try to store it (the INSERT would fail and break $expand). Issue #36.
  private static final long MAX_EXPANSION_BYTES = 900L * 1024 * 1024;

  public void save(ValueSetSnapshot snapshot) {
    byte[] gz = SnapshotExpansionCodec.encode(snapshot.getExpansion());
    if (gz.length > MAX_EXPANSION_BYTES) {
      log.warn("Value set {} version {} expansion not cached: {} concepts compress to {} MB, over the {} MB snapshot limit (issue #36)",
          snapshot.getValueSet(), snapshot.getValueSetVersion() == null ? null : snapshot.getValueSetVersion().getId(),
          snapshot.getConceptsTotal(), gz.length / (1024 * 1024), MAX_EXPANSION_BYTES / (1024 * 1024));
      return;
    }
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", snapshot.getId());
    ssb.property("value_set", snapshot.getValueSet());
    ssb.property("value_set_version_id", snapshot.getValueSetVersion().getId());
    ssb.property("concepts_total", snapshot.getConceptsTotal());
    ssb.property("expansion_bytea", gz);
    ssb.property("expansion", "?::jsonb", (Object) null); // clear any legacy uncompressed jsonb on (re)generation
    ssb.jsonProperty("dependencies", snapshot.getDependencies(), false);
    ssb.property("created_at", snapshot.getCreatedAt());
    ssb.property("created_by", snapshot.getCreatedBy());
    SqlBuilder sb = ssb.buildSave("terminology.value_set_snapshot", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    snapshot.setId(id);
  }

  public ValueSetSnapshot load(String valueSet, Long valueSetVersionId) {
    String sql = "select vss.id, vss.value_set, vss.concepts_total, vss.expansion::text as expansion_json, " +
        "vss.expansion_bytea, vss.dependencies::text as dependencies_json, vss.created_at, vss.created_by, " +
        "(select json_build_object('id', vsv.id, 'version', vsv.version) from terminology.value_set_version vsv " +
        " where vsv.id = vss.value_set_version_id and vsv.sys_status = 'A')::text as value_set_version_json " +
        "from terminology.value_set_snapshot vss " +
        "where vss.sys_status = 'A' and vss.value_set = ? and vss.value_set_version_id = ?";
    return jdbcTemplate.query(sql, rs -> {
      if (!rs.next()) {
        return null;
      }
      ValueSetSnapshot s = new ValueSetSnapshot();
      s.setId(rs.getLong("id"));
      s.setValueSet(rs.getString("value_set"));
      s.setConceptsTotal((Integer) rs.getObject("concepts_total"));
      // Read across both columns: gzip bytea (new) wins, legacy uncompressed jsonb is the fallback.
      byte[] gz = rs.getBytes("expansion_bytea");
      String expansionJson = gz != null ? SnapshotExpansionCodec.decodeToJson(gz) : rs.getString("expansion_json");
      s.setExpansion(expansionJson == null ? null : JsonUtil.fromJson(expansionJson, JsonUtil.getListType(ValueSetVersionConcept.class)));
      String dependenciesJson = rs.getString("dependencies_json");
      if (dependenciesJson != null) {
        s.setDependencies(JsonUtil.fromJson(dependenciesJson, JsonUtil.getListType(ValueSetSnapshotDependency.class)));
      }
      String vsvJson = rs.getString("value_set_version_json");
      if (vsvJson != null) {
        s.setValueSetVersion(JsonUtil.fromJson(vsvJson, ValueSetVersionReference.class));
      }
      s.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
      s.setCreatedBy(rs.getString("created_by"));
      return s;
    }, valueSet, valueSetVersionId);
  }

  /**
   * Returns ONLY {@code concepts_total} for the saved snapshot, without loading the
   * (potentially huge) expansion. Lets the FHIR read flow emit {@code expansion.total} on
   * {@code ?_summary=false} without paying for the full expansion list — used by
   * {@link org.termx.terminology.fhir.valueset.ValueSetResourceStorage} to populate the count on the
   * open-in-FHIR list link. Returns {@code null} when no snapshot exists.
   */
  public Integer loadConceptsTotal(String valueSet, Long valueSetVersionId) {
    String sql = "select concepts_total from terminology.value_set_snapshot " +
        "where sys_status = 'A' and value_set = ? and value_set_version_id = ? limit 1";
    return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getInt(1) : null, valueSet, valueSetVersionId);
  }

}
