package com.kodality.termserver.ts.valueset.ruleset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ValueSetVersionRuleRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersionRule.class, bp -> {
    bp.addColumnProcessor("concepts", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("filters", PgBeanProcessor.fromJson());
  });

  public void save(ValueSetVersionRule rule, Long ruleSetId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", rule.getId());
    ssb.property("type", rule.getType());
    ssb.property("code_system", rule.getCodeSystem());
    ssb.property("code_system_version_id", rule.getCodeSystemVersionId());
    ssb.jsonProperty("concepts", rule.getConcepts());
    ssb.jsonProperty("filters", rule.getFilters());
    ssb.property("value_set", rule.getValueSet());
    ssb.property("value_set_version_id", rule.getValueSetVersionId());
    ssb.property("rule_set_id", ruleSetId);
    SqlBuilder sb = ssb.buildSave("terminology.value_set_version_rule", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    rule.setId(id);
  }

  public ValueSetVersionRule load(Long id) {
    String sql = "select * from terminology.value_set_version_rule where sys_status = 'A' and id = ? ";
    return getBean(sql, bp, id);
  }

  public List<ValueSetVersionRule> loadAll(Long ruleSetId) {
    String sql = "select * from terminology.value_set_version_rule where sys_status = 'A' and rule_set_id = ?";
    return getBeans(sql, bp, ruleSetId);
  }

  public void retain(List<ValueSetVersionRule> rules, Long ruleSetId) {
    SqlBuilder sb = new SqlBuilder("update terminology.value_set_version_rule set sys_status = 'C'");
    sb.append(" where rule_set_id = ? and sys_status = 'A'", ruleSetId);
    sb.andNotIn("id", rules, ValueSetVersionRule::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.value_set_version_rule set sys_status = 'C' where id = ?", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
