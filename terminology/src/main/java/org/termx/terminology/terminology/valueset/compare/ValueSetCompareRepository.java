package org.termx.terminology.terminology.valueset.compare;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.util.JsonUtil;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.termx.terminology.terminology.valueset.snapshot.SnapshotExpansionCodec;
import org.termx.ts.valueset.ValueSetCompareResult;
import org.termx.ts.valueset.ValueSetVersionConcept;
import jakarta.inject.Singleton;

@Singleton
public class ValueSetCompareRepository extends BaseRepository {

  public ValueSetCompareResult compare(Long sourceVsVersionId, Long targetVsVersionId) {
    // Expansions used to be diffed with jsonb_array_elements in SQL, but they are now stored as
    // gzip-compressed bytea (issue #36) which SQL cannot unpack, so load and diff in-app. Reads
    // across both columns to stay compatible with legacy uncompressed snapshots.
    Set<String> source = codes(sourceVsVersionId);
    Set<String> target = codes(targetVsVersionId);

    ValueSetCompareResult result = new ValueSetCompareResult();
    target.stream().filter(c -> !source.contains(c)).forEach(c -> result.getAdded().add(c));
    source.stream().filter(c -> !target.contains(c)).forEach(c -> result.getDeleted().add(c));
    return result;
  }

  private Set<String> codes(Long valueSetVersionId) {
    String sql = "select vss.expansion::text as expansion_json, vss.expansion_bytea "
        + "from terminology.value_set_snapshot vss where vss.sys_status = 'A' and vss.value_set_version_id = ? limit 1";
    List<ValueSetVersionConcept> expansion = jdbcTemplate.query(sql, rs -> {
      if (!rs.next()) {
        return List.<ValueSetVersionConcept>of();
      }
      byte[] gz = rs.getBytes("expansion_bytea");
      String json = gz != null ? SnapshotExpansionCodec.decodeToJson(gz) : rs.getString("expansion_json");
      return json == null ? List.<ValueSetVersionConcept>of()
          : JsonUtil.fromJson(json, JsonUtil.getListType(ValueSetVersionConcept.class));
    }, valueSetVersionId);
    return expansion.stream()
        .map(c -> c.getConcept() == null ? null : c.getConcept().getCode())
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }
}
