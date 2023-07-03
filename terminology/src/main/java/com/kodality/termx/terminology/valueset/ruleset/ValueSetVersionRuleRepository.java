package com.kodality.termx.terminology.valueset.ruleset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ValueSetVersionRuleRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersionRule.class, bp -> {
    bp.addColumnProcessor("value_set_version", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("code_system_version", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("concepts", PgBeanProcessor.fromJson(JsonUtil.getListType(ValueSetVersionConcept.class)));
    bp.addColumnProcessor("filters", PgBeanProcessor.fromJson(JsonUtil.getListType(ValueSetRuleFilter.class)));
  });

  private final static String select = "select vsvr.*, " +
      "(select json_build_object('id', vsv.id, 'version', vsv.version) from terminology.value_set_version vsv where vsv.id = vsvr.value_set_version_id and vsv.sys_status = 'A') as value_set_version, " +
      "(select json_build_object('id', csv.id, 'version', csv.version) from terminology.code_system_version csv where csv.id = vsvr.code_system_version_id and csv.sys_status = 'A') as code_system_version ";

  public void save(ValueSetVersionRule rule, Long ruleSetId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", rule.getId());
    ssb.property("type", rule.getType());
    ssb.property("code_system", rule.getCodeSystem());
    ssb.property("code_system_version_id", rule.getCodeSystemVersion() == null ? null : rule.getCodeSystemVersion().getId());
    ssb.jsonProperty("concepts", rule.getConcepts());
    ssb.jsonProperty("filters", rule.getFilters());
    ssb.property("value_set", rule.getValueSet());
    ssb.property("value_set_version_id", rule.getValueSetVersion() == null ? null : rule.getValueSetVersion().getId());
    ssb.property("rule_set_id", ruleSetId);
    SqlBuilder sb = ssb.buildSave("terminology.value_set_version_rule", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    rule.setId(id);
  }

  public ValueSetVersionRule load(Long id) {
    String sql = select + "from terminology.value_set_version_rule vsvr where vsvr.sys_status = 'A' and vsvr.id = ? ";
    return getBean(sql, bp, id);
  }

  public List<ValueSetVersionRule> loadAll(Long ruleSetId) {
    String sql = select + "from terminology.value_set_version_rule vsvr where vsvr.sys_status = 'A' and vsvr.rule_set_id = ?";
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

  public QueryResult<ValueSetVersionRule> query(ValueSetVersionRuleQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.value_set_version_rule vsvr where vsvr.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from terminology.value_set_version_rule vsvr where vsvr.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetVersionRuleQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and vsvr.code_system = ?", params.getCodeSystem());
    if (StringUtils.isNotEmpty(params.getCodeSystemVersionIds())) {
      sb.and().in("vsvr.code_system_version_id", params.getCodeSystemVersionIds(), Long::valueOf);
    }

    sb.appendIfNotNull("and vsvr.value_set = ?", params.getValueSet());
    if (StringUtils.isNotEmpty(params.getValueSetVersionIds())) {
      sb.and().in("vsvr.value_set_version_id", params.getValueSetVersionIds(), Long::valueOf);
    }
    return sb;
  }
}
