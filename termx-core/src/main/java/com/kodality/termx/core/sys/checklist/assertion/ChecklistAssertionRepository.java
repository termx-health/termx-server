package com.kodality.termx.core.sys.checklist.assertion;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.sys.checklist.ChecklistAssertion;
import com.kodality.termx.sys.checklist.ChecklistAssertionQueryParams;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class ChecklistAssertionRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ChecklistAssertion.class, bp -> {
    bp.addColumnProcessor("rule", PgBeanProcessor.fromJson());
  });

  private final static String select =
      "select c.*, " +
          "(select json_build_object('id', cr.id, 'code', cr.code, 'title', cr.title, 'description', cr.description, 'active', cr.active, 'type', cr.type, " +
          "                          'verification', cr.verification, 'severity', cr.severity, 'target', cr.target, 'resource_type', cr.resource_type) " +
          "   from sys.checklist_rule cr where cr.sys_status = 'A' and cr.id = ca.rule_id) as rule ";

  public void save(ChecklistAssertion assertion) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", assertion.getId());
    ssb.property("checklist_id", assertion.getChecklistId());
    ssb.property("rule_id", assertion.getRuleId());
    ssb.property("resource_version", assertion.getResourceVersion());
    ssb.property("passed", assertion.isPassed());
    ssb.property("executor", assertion.getExecutor());
    ssb.property("execution_date", assertion.getExecutionDate());
    ssb.jsonProperty("errors", assertion.getErrors());
    SqlBuilder sb = ssb.buildSave("sys.checklist_assertion", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    assertion.setId(id);
  }


  public QueryResult<ChecklistAssertion> query(ChecklistAssertionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.checklist_assertion ca");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from sys.checklist_assertion ca");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ChecklistAssertionQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where ca.sys_status = 'A'");

    if (StringUtils.isNotEmpty(params.getChecklistResource())) {
      String[] resource = PipeUtil.parsePipe(params.getChecklistResource());
      sb.append("and exists(select 1 from sys.checklist c where c.id = ca.checklist_id and c.sys_status = 'A' and c.resource_type = ? and c.resource_id = ?)",
          resource[0], resource[1]);
    }
    return sb;
  }
}
