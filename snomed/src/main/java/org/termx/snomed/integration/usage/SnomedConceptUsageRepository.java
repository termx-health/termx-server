package org.termx.snomed.integration.usage;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.termx.snomed.concept.SnomedConceptUsage;

@Singleton
public class SnomedConceptUsageRepository extends BaseRepository {

  private static final String SNOMED_CS = "snomed-ct";
  private static final String SNOMED_URI = "http://snomed.info/sct";

  private final RowMapper<SnomedConceptUsage> mapper = (rs, rowNum) -> new SnomedConceptUsage()
      .setResourceType(rs.getString("resource_type"))
      .setResourceId(rs.getString("resource_id"))
      .setResourceVersion(rs.getString("resource_version"))
      .setConceptCode(rs.getString("concept_code"))
      .setLocation(rs.getString("location"));

  public List<SnomedConceptUsage> findInSupplements(List<String> codes) {
    if (codes == null || codes.isEmpty()) {
      return Collections.emptyList();
    }
    SqlBuilder sb = new SqlBuilder();
    sb.append("select 'CodeSystemSupplement' as resource_type,");
    sb.append("       cs.id                  as resource_id,");
    sb.append("       null::text             as resource_version,");
    sb.append("       cev.code               as concept_code,");
    sb.append("       'concept'              as location");
    sb.append("  from terminology.code_system_entity_version cev");
    sb.append("  join terminology.code_system cs on cs.id = cev.code_system");
    sb.append(" where cs.base_code_system = ?", SNOMED_CS);
    sb.append("   and cev.sys_status = 'A'");
    sb.append("   and cs.sys_status = 'A'");
    sb.and().in("cev.code", codes);
    return jdbcTemplate.query(sb.getSql(), mapper, sb.getParams());
  }

  public List<SnomedConceptUsage> findInValueSetRules(List<String> codes) {
    if (codes == null || codes.isEmpty()) {
      return Collections.emptyList();
    }
    SqlBuilder sb = new SqlBuilder();
    sb.append("select 'ValueSet'                                         as resource_type,");
    sb.append("       vsv.value_set                                      as resource_id,");
    sb.append("       vsv.version                                        as resource_version,");
    sb.append("       coalesce(c->'concept'->>'code', c->>'code')        as concept_code,");
    sb.append("       'rule'                                             as location");
    sb.append("  from terminology.value_set_version_rule vsr");
    sb.append("  join terminology.value_set_version_rule_set vsrs on vsrs.id = vsr.rule_set_id");
    sb.append("  join terminology.value_set_version vsv on vsv.id = vsrs.value_set_version_id");
    sb.append("  cross join lateral jsonb_array_elements(coalesce(vsr.concepts, '[]'::jsonb)) c");
    sb.append(" where vsr.sys_status = 'A'");
    sb.append("   and vsrs.sys_status = 'A'");
    sb.append("   and vsv.sys_status = 'A'");
    sb.append("   and (vsr.code_system = ?", SNOMED_CS);
    sb.append("        or coalesce(c->'concept'->>'codeSystem', c->>'codeSystem') = ?", SNOMED_CS);
    sb.append("        or coalesce(c->'concept'->>'codeSystemUri', c->>'codeSystemUri') = ?)", SNOMED_URI);
    sb.and().in("coalesce(c->'concept'->>'code', c->>'code')", codes);
    return jdbcTemplate.query(sb.getSql(), mapper, sb.getParams());
  }

  public List<SnomedConceptUsage> findInValueSetExpansions(List<String> codes) {
    if (codes == null || codes.isEmpty()) {
      return Collections.emptyList();
    }
    SqlBuilder sb = new SqlBuilder();
    sb.append("select 'ValueSetExpansion'                                as resource_type,");
    sb.append("       vss.value_set                                      as resource_id,");
    sb.append("       vsv.version                                        as resource_version,");
    sb.append("       coalesce(c->'concept'->>'code', c->>'code')        as concept_code,");
    sb.append("       'expansion'                                        as location");
    sb.append("  from terminology.value_set_snapshot vss");
    sb.append("  join terminology.value_set_version vsv on vsv.id = vss.value_set_version_id");
    sb.append("  cross join lateral jsonb_array_elements(coalesce(vss.expansion, '[]'::jsonb)) c");
    sb.append(" where vss.sys_status = 'A'");
    sb.append("   and vsv.sys_status = 'A'");
    sb.append("   and (coalesce(c->'concept'->>'codeSystem', c->>'codeSystem') = ?", SNOMED_CS);
    sb.append("        or coalesce(c->'concept'->>'codeSystemUri', c->>'codeSystemUri') = ?)", SNOMED_URI);
    sb.and().in("coalesce(c->'concept'->>'code', c->>'code')", codes);
    return jdbcTemplate.query(sb.getSql(), mapper, sb.getParams());
  }

  public List<SnomedConceptUsage> findAll(List<String> codes) {
    List<SnomedConceptUsage> all = new ArrayList<>();
    all.addAll(findInSupplements(codes));
    all.addAll(findInValueSetRules(codes));
    all.addAll(findInValueSetExpansions(codes));
    return all;
  }
}
