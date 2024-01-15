package com.kodality.termx.core.sys.checklist;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.sys.checklist.ChecklistRule;
import com.kodality.termx.sys.checklist.ChecklistRuleQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class ChecklistRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ChecklistRule.class, bp -> {
    bp.addColumnProcessor("title", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
  });

  public void save(ChecklistRule rule) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", rule.getId());
    ssb.property("code", rule.getCode());
    ssb.jsonProperty("title", rule.getTitle());
    ssb.jsonProperty("description", rule.getDescription());
    ssb.property("active", rule.isActive());
    ssb.property("type", rule.getType());
    ssb.property("verification", rule.getVerification());
    ssb.property("severity", rule.getSeverity());
    ssb.property("target", rule.getTarget());
    ssb.property("resource_type", rule.getResourceType());

    SqlBuilder sb = ssb.buildSave("sys.checklist_rule", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    rule.setId(id);
  }

  public ChecklistRule load(Long id) {
    String sql = "select * from sys.checklist_rule where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public ChecklistRule load(String code) {
    String sql = "select * from sys.checklist_rule where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public QueryResult<ChecklistRule> query(ChecklistRuleQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.checklist_rule cr");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from sys.checklist_rule cr");
      sb.append(filter(params));
      sb.append(order(params, sortMap()));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ChecklistRuleQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where cr.sys_status = 'A'");
    sb.and().in("cr.id", params.getPermittedIds());
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (cr.code ~* ? or exists (select 1 from jsonb_each_text(cr.title) where value ~* ?) or exists (select 1 from jsonb_each_text(cr.description) where value ~* ?))",
          params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    return sb;
  }

  private Map<String, String> sortMap() {
    Map<String, String> sortMap = new HashMap<>(Map.of("code", "cr.code"));
    return sortMap;
  }


}
