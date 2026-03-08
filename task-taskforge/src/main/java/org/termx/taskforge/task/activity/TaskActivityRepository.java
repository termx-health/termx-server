package org.termx.taskforge.task.activity;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import org.termx.taskforge.api.TaskforgeUserProvider;
import org.termx.taskforge.task.activity.TaskActivity.TaskActivityContextItem;
import org.termx.taskforge.task.activity.TaskActivity.TaskActivityTransition;
import io.micronaut.core.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class TaskActivityRepository extends BaseRepository {
  @Inject
  private TaskforgeUserProvider userProvider;

  private final PgBeanProcessor bp = new PgBeanProcessor(TaskActivity.class, p -> {
    p.addColumnProcessor("updated_by", "updatedBy", TaskforgeUserProvider::ofReference);
    p.addColumnProcessor("transition", PgBeanProcessor.fromJson(JsonUtil.getMapType(TaskActivityTransition.class)));
    p.addColumnProcessor("context", PgBeanProcessor.fromJson(JsonUtil.getListType(TaskActivityContextItem.class)));
  });

  private final Map<String, String> orderMapping = Map.of(
      TaskActivitySearchParams.Ordering.author, "ta.updated_by",
      TaskActivitySearchParams.Ordering.updated, "ta.updated_at"
  );


  private static String select() {
    return "SELECT ta.* FROM taskforge.task_activity ta";
  }

  public TaskActivity load(Long activityId) {
    String sql = select() + " WHERE ta.sys_status = 'A' and ta.id = ?";
    return getBean(sql, bp, activityId);
  }

  public List<TaskActivity> search(TaskActivitySearchParams params, String tenant) {
    SqlBuilder sb = new SqlBuilder(select() + " INNER JOIN taskforge.task t on t.id = ta.task_id");
    sb.append(filter(params, tenant));
    sb.append(order(params.getSort(), orderMapping));
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  private SqlBuilder filter(TaskActivitySearchParams params, String tenant) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("WHERE ta.sys_status = 'A' and core.aclchk(t.project_id, ?)", tenant);
    sb.appendIfNotNull(params.getTaskIds(), (s, p) -> s.and().in("ta.task_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("ta.id", p, Long::valueOf));
    if (StringUtils.isNotEmpty(params.getNoteContains())) {
      sb.and("ta.note ilike '%' || ? || '%'", params.getNoteContains());
    }
    sb.appendIfNotNull(params.getUpdatedBy(), (s, p) -> s.and().in("ta.updated_by", p));
    sb.appendIfNotNull("and ta.updated_at >= ?::timestamptz", params.getUpdatedGe());
    sb.appendIfNotNull("and ta.updated_at <= ?::timestamptz", params.getUpdatedLe());
    if (StringUtils.isNotEmpty(params.getContext())) {
      sb.append("and ( 1<>1 ");
      Arrays.stream(params.getContext().split(",")).forEach(ctx -> {
        String[] pipe = PipeUtil.parsePipe(ctx);
        sb.append("or exists( select 1 from jsonb_array_elements(ta.context) ctx where (ctx ->> 'type')::text = ? and (ctx ->> 'id')::text = ? )", pipe[0], pipe[1]);
      });
      sb.append(")");
    }
    return sb;
  }

  public void save(TaskActivity ta) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", ta.getId());
    ssb.property("task_id", ta.getTaskId());
    ssb.property("note", ta.getNote());
    ssb.property("updated_at", ta.getUpdatedAt());
    ssb.property("updated_by", userProvider.getReferenceId(ta.getUpdatedBy()));
    ssb.jsonProperty("transition", ta.getTransition());
    ssb.jsonProperty("context", ta.getContext());
    SqlBuilder sb = ssb.buildSave("taskforge.task_activity", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    ta.setId(id);
  }

  public void cancel(Long activityId) {
    SqlBuilder sb = new SqlBuilder("update taskforge.task_activity set sys_status = 'C' where id = ? and sys_status = 'A'", activityId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
