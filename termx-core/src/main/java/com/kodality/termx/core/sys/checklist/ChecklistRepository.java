package com.kodality.termx.core.sys.checklist;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.sys.checklist.Checklist;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion;
import com.kodality.termx.sys.checklist.ChecklistQueryParams;
import java.util.List;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class ChecklistRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Checklist.class, bp -> {
    bp.addColumnProcessor("rule", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("whitelist", PgBeanProcessor.fromJson(JsonUtil.getListType(ChecklistWhitelist.class)));
    bp.addColumnProcessor("assertions", PgBeanProcessor.fromJson(JsonUtil.getListType(ChecklistAssertion.class)));
  });

  private final static String select =
      "select c.*, " +
          "(select json_build_object('id', cr.id, 'code', cr.code, 'title', cr.title, 'description', cr.description, 'active', cr.active, 'type', cr.type, " +
          "                          'verification', cr.verification, 'severity', cr.severity, 'target', cr.target, 'resource_type', cr.resource_type) " +
          "   from sys.checklist_rule cr where cr.sys_status = 'A' and cr.id = c.rule_id) as rule, " +
          "(select jsonb_agg(json_build_object('id', cw.id, 'resourceType', cw.resource_type, 'resourceId', cw.resource_id))" +
          "   from sys.checklist_whitelist cw where cw.sys_status = 'A' and cw.checklist_id = c.id) whitelist ";

  public void save(Checklist c) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", c.getId());
    ssb.property("rule_id", c.getRule().getId());
    ssb.property("resource_type", c.getResourceType());
    ssb.property("resource_id", c.getResourceId());
    SqlBuilder sb = ssb.buildSave("sys.checklist", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    c.setId(id);
  }

  public Checklist load(Long id) {
    String sql = select + "from sys.checklist c where c.id = ? and c.sys_status = 'A'";
    return getBean(sql, bp, id);
  }


  public QueryResult<Checklist> query(ChecklistQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.checklist c");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + selectAssertions(params.isAssertionsDecorated(), params.getResourceVersion()) + "from sys.checklist c");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private String selectAssertions(boolean assertionsDecorated, String resourceVersion) {
    if (!assertionsDecorated) {
      return "";
    }
    SqlBuilder sb = new SqlBuilder(", (select jsonb_agg(ca.a) from (select json_build_object(" +
        "'id', ca.id, 'resourceVersion', ca.resource_version, 'passed', ca.passed, 'executor', ca.executor, 'executionDate', ca.execution_date, 'errors', ca.errors) as a " +
            "from sys.checklist_assertion ca where ca.sys_status = 'A' and ca.checklist_id = c.id");
    sb.appendIfNotNull("and ca.resource_version = ?", resourceVersion);
    sb.append("order by ca.execution_date desc) ca) as assertions ");
    return sb.toPrettyString();
  }

  private SqlBuilder filter(ChecklistQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where c.sys_status = 'A'");
    if (StringUtils.isNotEmpty(params.getIds())) {
      sb.and().in("c.id", params.getIds(), Long::valueOf);
    }
    sb.appendIfNotNull("and c.resource_type = ?", params.getResourceType());
    sb.appendIfNotNull("and c.resource_id = ?", params.getResourceId());
    if (params.getRuleTarget() != null || params.getRuleVerification() != null) {
      sb.append("and exists (select 1 from sys.checklist_rule cr where cr.sys_status = 'A' and cr.id = c.rule_id");
      sb.appendIfNotNull("and cr.target = ?", params.getRuleTarget());
      sb.appendIfNotNull("and cr.verification = ?", params.getRuleVerification());
      sb.append(")");
    }
    return sb;
  }

  public void retain(String resourceType, String resourceId, List<Checklist> checklist) {
    SqlBuilder sb = new SqlBuilder("update sys.checklist set sys_status = 'C'");
    sb.append(" where resource_type = ? and resource_id = ? and sys_status = 'A'", resourceType, resourceId);
    sb.andNotIn("id", checklist, Checklist::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

}
