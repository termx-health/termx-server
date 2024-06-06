package com.kodality.termx.terminology.terminology.valueset.compare;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.termx.ts.valueset.ValueSetCompareResult;
import jakarta.inject.Singleton;

@Singleton
public class ValueSetCompareRepository extends BaseRepository {
  public ValueSetCompareResult compare(Long sourceVsVersionId, Long targetVsVersionId) {
    String s = "select jsonb_array_elements(vss.expansion) from terminology.value_set_snapshot vss" +
        " where vss.sys_status = 'A' and vss.value_set_version_id = ? ";
    String sql = "with  c1 as ( " + s + " ), " +
        "               c2 as ( " + s + " ) " +
        "select 'deleted' t, jsonb_array_elements(c1) -> 'concept' ->> 'code' code from c1 " +
        "  where not exists (select 1 from c2 where (jsonb_array_elements(c1) -> 'concept' ->> 'code') = (jsonb_array_elements(c2) -> 'concept' ->> 'code')) " +
        "union all " +
        "select 'added' t, jsonb_array_elements(c2) -> 'concept' ->> 'code' code from c2 " +
        "  where not exists (select 1 from c1 where (jsonb_array_elements(c1) -> 'concept' ->> 'code') = (jsonb_array_elements(c2) -> 'concept' ->> 'code'))";
    return jdbcTemplate.query(sql, rs -> {
      ValueSetCompareResult r = new ValueSetCompareResult();
      while (rs.next()) {
        switch (rs.getString("t")) {
          case "added" -> r.getAdded().add(rs.getString("code"));
          case "deleted" -> r.getDeleted().add(rs.getString("code"));
        }
      }
      return r;
    }, sourceVsVersionId, targetVsVersionId);
  }
}
