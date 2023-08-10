package com.kodality.termx.terminology.codesystem.compare;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.codesystem.compare.CodeSystemCompareResult.CodeSystemCompareResultChange;
import com.kodality.termx.terminology.codesystem.compare.CodeSystemCompareResult.CodeSystemCompareResultDiff;
import jakarta.inject.Singleton;

@Singleton
public class CodeSystemCompareRepository extends BaseRepository {
  public CodeSystemCompareResult compare(Long sourceCsVersionId, Long targetCsVersionId) {
    String s = "select c.code, csev.status, csev.id version_id, csev.description," +
        "        (select jsonb_agg(ep.name || '|' || epv.value::text order by ep.name, epv.value::text)  " +
        "          from terminology.entity_property_value epv, terminology.entity_property ep " +
        "         where epv.entity_property_id = ep.id and epv.code_system_entity_version_id = csev.id) properties, " +
        "        (select jsonb_agg(ep.name || '|' || d.language || '|' || d.name::text order by ep.name, d.language, d.name::text)  " +
        "           from terminology.designation d, terminology.entity_property ep " +
        "         where d.designation_type_id = ep.id and d.code_system_entity_version_id  = csev.id) designations " +
        "   from terminology.code_system_version csv" +
        "       inner join terminology.entity_version_code_system_version_membership m on m.code_system_version_id = csv.id" +
        "       inner join terminology.code_system_entity_version csev on csev.code_system = csv.code_system and m.code_system_entity_version_id = csev.id" +
        "       inner join terminology.code_system_entity cse on cse.id  = csev.code_system_entity_id" +
        "       inner join terminology.concept c on c.id = csev.code_system_entity_id" +
        " where c.sys_status = 'A' and cse.sys_status = 'A' and csev.sys_status = 'A' and m.sys_status = 'A'" +
        "    and csv.id = ? ";
    String sql = "with  c1 as ( " + s + " ), " +
        "               c2 as ( " + s + " ) " +
        "select 'changed' t, c1.code, jsonb_strip_nulls( jsonb_build_object(" +
        "       'old', jsonb_build_object(" +
        "             'status', case when c1.status <> c2.status then c1.status else null end, " +
        "             'description', case when coalesce(c1.description,'') <> coalesce(c2.description,'') then c1.description else null end, " +
        "             'properties', case when coalesce(c1.properties,'{}'::jsonb) <> coalesce(c2.properties,'{}'::jsonb) then c1.properties else null end," +
        "             'designations', case when coalesce(c1.designations,'{}'::jsonb) <> coalesce(c2.designations,'{}'::jsonb) then c1.designations else null end" +
        "             ), " +
        "       'mew', jsonb_build_object(" +
        "             'status', case when c1.status <> c2.status then c2.status else null end, " +
        "             'description', case when coalesce(c1.description,'') <> coalesce(c2.description,'') then c2.description else null end, " +
        "             'properties', case when coalesce(c1.properties,'{}'::jsonb) <> coalesce(c2.properties,'{}'::jsonb) then c2.properties else null end," +
        "             'designations', case when coalesce(c1.designations,'{}'::jsonb) <> coalesce(c2.designations,'{}'::jsonb) then c2.designations else null end" +
        "             )" +
        "       )) diff " +
        "  from c1, c2 " +
        " where c1.code = c2.code and c1.version_id != c2.version_id " +
        "   and (c1.status <> c2.status or coalesce(c1.description,'') <> coalesce(c2.description,'') or" +
        "        coalesce(c1.properties,'{}'::jsonb) <> coalesce(c2.properties,'{}'::jsonb) or" +
        "        coalesce(c1.designations,'{}'::jsonb) <> coalesce(c2.designations,'{}'::jsonb))" +
        "union all " +
        "        select 'deleted' t, c1.code, null from c1 where not exists (select 1 from c2 where c1.code = c2.code) " +
        "union all " +
        "select 'added' t, c2.code, null from c2 " +
        "  where not exists (select 1 from c1 where c1.code = c2.code)";
    return jdbcTemplate.query(sql, rs -> {
      CodeSystemCompareResult r = new CodeSystemCompareResult();
      while (rs.next()) {
        switch (rs.getString("t")) {
          case "added" -> r.getAdded().add(rs.getString("code"));
          case "deleted" -> r.getDeleted().add(rs.getString("code"));
          case "changed" -> r.getChanged().add(new CodeSystemCompareResultChange()
              .setCode(rs.getString("code"))
              .setDiff(JsonUtil.fromJson(rs.getString("diff"), CodeSystemCompareResultDiff.class))
          );
        }
      }
      return r;
    }, sourceCsVersionId, targetCsVersionId);
  }
}
