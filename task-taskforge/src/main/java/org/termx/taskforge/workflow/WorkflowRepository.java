package org.termx.taskforge.workflow;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.Jsonb;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import org.termx.taskforge.workflow.Workflow.WorkflowTransition;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class WorkflowRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Workflow.class, p -> {
    p.addColumnProcessor("transitions", PgBeanProcessor.fromJson(JsonUtil.getListType(WorkflowTransition.class)));
  });

  public static String jsonb(String alias) {
    return Jsonb.object("id", alias + ".id", "taskType", alias + ".task_type", "transitions", alias + ".transitions");
  }

  public void save(Long projectId, List<Workflow> workflows) {
    workflows.forEach(w -> {
      SaveSqlBuilder ssb = new SaveSqlBuilder();
      ssb.property("id", w.getId());
      ssb.property("project_id", projectId);
      ssb.property("task_type", w.getTaskType());
      ssb.jsonProperty("transitions", w.getTransitions());
      SqlBuilder sb = ssb.buildSave("taskforge.workflow", "id");
      Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
      w.setId(id);
    });
  }

  public Workflow load(Long id) {
    String sql = "select * from taskforge.workflow where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public QueryResult<Workflow> search(WorkflowSearchParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("SELECT count(1) FROM taskforge.workflow w ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("SELECT * FROM taskforge.workflow w");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(WorkflowSearchParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("where w.sys_status = 'A'");
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("w.id", p, Long::valueOf));
    sb.appendIfNotNull(params.getProjectIds(), (s, p) -> s.and().in("w.project_id", p, Long::valueOf));
    if (params.getProjectCodes() != null) {
      sb.append("and exists (select 1 from taskforge.project p where p.id = w.project_id and p.sys_status = 'A'");
      sb.and().in("p.code", params.getProjectCodes());
      sb.append(")");
    }
    sb.appendIfNotNull(params.getProjectIds(), (s, p) -> s.and().in("w.project_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getTypes(), (s, p) -> s.and().in("w.task_type", p));
    return sb;
  }
}
