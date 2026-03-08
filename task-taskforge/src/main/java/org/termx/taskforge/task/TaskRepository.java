package org.termx.taskforge.task;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import org.termx.taskforge.api.TaskforgeUserProvider;
import org.termx.taskforge.task.Task.TaskContextItem;
import io.micronaut.core.util.StringUtils;
import java.util.Arrays;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class TaskRepository extends BaseRepository {
  @Inject
  private TaskforgeUserProvider userProvider;

  private final PgBeanProcessor bp = new PgBeanProcessor(Task.class, p -> {
    p.addColumnProcessor("created_by", "createdBy", TaskforgeUserProvider::ofReference);
    p.addColumnProcessor("updated_by", "updatedBy", TaskforgeUserProvider::ofReference);
    p.addColumnProcessor("assignee", "assignee", TaskforgeUserProvider::ofReference);
    p.addColumnProcessor("context", PgBeanProcessor.fromJson(JsonUtil.getListType(TaskContextItem.class)));
  });
  private final Map<String, String> orderMapping = Map.of(
      TaskSearchParams.Ordering.number, "t.number",
      TaskSearchParams.Ordering.title, "t.title",
      TaskSearchParams.Ordering.type, "t.type",
      TaskSearchParams.Ordering.status, "t.status",
      TaskSearchParams.Ordering.author, "t.created_by",
      TaskSearchParams.Ordering.assignee, "t.assignee",
      TaskSearchParams.Ordering.created, "t.created_at",
      TaskSearchParams.Ordering.updated, "t.updated_at"
  );

  private static String select() {
    return "SELECT * FROM taskforge.task t";
  }

  public Long save(Task t) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", t.getId());
    ssb.property("project_id", t.getProjectId());
    ssb.property("workflow_id", t.getWorkflowId());
    ssb.property("parent_id", t.getParentId());
    ssb.property("number", t.getNumber());
    ssb.property("type", t.getType());
    ssb.property("status", t.getStatus());
    ssb.property("business_status", t.getBusinessStatus());
    ssb.property("priority", t.getPriority());
    if (t.getId() == null) { // only on insert
      ssb.property("created_by", userProvider.getReferenceId(t.getCreatedBy()));
      ssb.property("created_at", t.getCreatedAt());
    }
    ssb.property("assignee", userProvider.getReferenceId(t.getAssignee()));
    ssb.property("updated_at", t.getUpdatedAt());
    ssb.property("updated_by", userProvider.getReferenceId(t.getUpdatedBy()));
    ssb.property("title", t.getTitle());
    ssb.property("content", t.getContent());
    ssb.jsonProperty("context", t.getContext());

    SqlBuilder sb = ssb.buildSave("taskforge.task", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public void updateStatus(Long id, String status) {
    String sql = "update taskforge.task set status = ? where status != ? and sys_status = 'A' and id = ?";
    jdbcTemplate.update(sql, status, status, id);
  }

  public Task load(Long id, String tenant) {
    SqlBuilder sb = new SqlBuilder(select() + " where t.id = ? and core.aclchk(t.project_id, ?)", id, tenant);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public Task load(String number, String tenant) {
    SqlBuilder sb = new SqlBuilder(select() + " where t.number = ? and core.aclchk(t.project_id, ?)", number, tenant);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public QueryResult<Task> search(TaskSearchParams params, String tenant) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("SELECT count(1) FROM taskforge.task t ");
      sb.append(joinReadLog(params));
      sb.append(filter(params, tenant));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select());
      sb.append(joinReadLog(params));
      sb.append(filter(params, tenant));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder joinReadLog(TaskSearchParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (Boolean.TRUE.equals(params.getUnseenChanges()) && params.getUnseenChangesUser() != null) {
      sb.append("LEFT JOIN taskforge.task_read_log trl ON trl.task_id = t.id AND trl.user_id = ? AND trl.sys_status = 'A'",
          params.getUnseenChangesUser());
    }
    return sb;
  }

  private SqlBuilder filter(TaskSearchParams params, String tenant) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("WHERE t.sys_status = 'A' and core.aclchk(t.project_id, ?)", tenant);
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("t.id", p, Long::valueOf));
    sb.appendIfNotNull(params.getProjectIds(), (s, p) -> s.and().in("t.project_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getStatuses(), (s, p) -> s.and().in("t.status", p));
    sb.appendIfNotNull(params.getStatusesNe(), (s, p) -> s.and().notIn("t.status", p));
    sb.appendIfNotNull(params.getPriorities(), (s, p) -> s.and().in("t.priority", p));
    sb.appendIfNotNull(params.getTypes(), (s, p) -> s.and().in("t.type", p));
    sb.appendIfNotNull(params.getAssignees(), (s, p) -> s.and().in("t.assignee", p));
    sb.appendIfNotNull(params.getCreatedBy(), (s, p) -> s.and().in("t.created_by", p));
    sb.appendIfNotNull("and t.created_at >= ?::timestamptz", params.getCreatedGe());
    sb.appendIfNotNull("and t.created_at <= ?::timestamptz", params.getCreatedLe());
    sb.appendIfNotNull("and t.modified_at >= ?::timestamptz", params.getModifiedGe());
    sb.appendIfNotNull("and t.modified_at <= ?::timestamptz", params.getModifiedLe());
    if (StringUtils.isNotEmpty(params.getContext())) {
      sb.append("and ( 1<>1 ");
      Arrays.stream(params.getContext().split(",")).forEach(ctx -> {
        String[] pipe = PipeUtil.parsePipe(ctx);
        sb.append("or exists( select 1 from jsonb_array_elements(t.context) ctx where (ctx ->> 'type')::text = ? and (ctx ->> 'id')::text = ? )", pipe[0], pipe[1]);
      });
      sb.append(")");
    }
    sb.appendIfNotNull(params.getCreatedByOrAssignee(), (s, p) ->
        s.append("and (t.created_by = ? or t.assignee = ?)", p, p));
    if (params.getPermittedContexts() != null) {
      sb.append("and (t.context is null");
      for (String ctx : params.getPermittedContexts()) {
        String[] pipe = PipeUtil.parsePipe(ctx);
        sb.append("or exists(select 1 from jsonb_array_elements(t.context) c where (c->>'type')::text = ? and (c->>'id')::text = ?)", pipe[0], pipe[1]);
      }
      sb.append(")");
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (");
      sb.append("t.number ilike '%' || ? || '%'  ", params.getTextContains());
      sb.or("t.title ilike '%' || ? || '%'", params.getTextContains());
      sb.or("t.content ilike '%' || ? || '%'", params.getTextContains());
      sb.append(")");
    }
    if (Boolean.TRUE.equals(params.getUnseenChanges()) && params.getUnseenChangesUser() != null) {
      sb.append("and (trl.last_opened_time is null or trl.last_opened_time < t.updated_at)");
    }
    return sb;
  }

}
