package com.kodality.termx.terminology.terminology.valueset.ruleset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import jakarta.inject.Singleton;

@Singleton
public class ValueSetVersionRuleSetRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersionRuleSet.class);

  public void save(ValueSetVersionRuleSet ruleSet, Long valueSetVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", ruleSet.getId());
    ssb.property("locked_date", ruleSet.getLockedDate());
    ssb.property("inactive", ruleSet.isInactive());
    ssb.property("value_set_version_id", valueSetVersionId);
    SqlBuilder sb = ssb.buildSave("terminology.value_set_version_rule_set", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    ruleSet.setId(id);
  }

  public ValueSetVersionRuleSet load(Long valueSetVersionId) {
    String sql = "select * from terminology.value_set_version_rule_set where sys_status = 'A' and value_set_version_id = ? ";
    return getBean(sql, bp, valueSetVersionId);
  }

  public ValueSetVersionRuleSet load(String valueSet, String valueSetVersion) {
    String sql = "select * from terminology.value_set_version_rule_set vsvrs where vsvrs.sys_status = 'A' " +
        "and exists(select 1 from terminology.value_set_version vsv where vsv.id = vsvrs.value_set_version_id and vsv.value_set = ? and vsv.version = ? and vsv.sys_status = 'A')";
    return getBean(sql, bp, valueSet, valueSetVersion);
  }

}
