package com.kodality.termserver.ts.valueset.ruleset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import javax.inject.Singleton;

@Singleton
public class ValueSetVersionRuleSetRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersionRuleSet.class);

  public void save(ValueSetVersionRuleSet ruleSet, Long valueSetVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", ruleSet.getId());
    ssb.property("locked_date", ruleSet.getLockedDate());
    ssb.property("inactive", ruleSet.getInactive());
    ssb.property("value_set_version_id", valueSetVersionId);
    SqlBuilder sb = ssb.buildSave("terminology.value_set_version_rule_set", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    ruleSet.setId(id);
  }

  public ValueSetVersionRuleSet load(Long valueSetVersionId) {
    String sql = "select * from terminology.value_set_version_rule_set where sys_status = 'A' and value_set_version_id = ? ";
    return getBean(sql, bp, valueSetVersionId);
  }
}
