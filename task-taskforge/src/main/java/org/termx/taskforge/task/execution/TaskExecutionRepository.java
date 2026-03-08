package org.termx.taskforge.task.execution;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import org.termx.taskforge.api.TaskforgeUserProvider;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class TaskExecutionRepository extends BaseRepository {
  @Inject
  private TaskforgeUserProvider userProvider;

  private final PgBeanProcessor bp = new PgBeanProcessor(TaskExecution.class, p -> {
    p.addColumnProcessor("created_by_", "createdBy", PgBeanProcessor.fromJson());
    p.addColumnProcessor("updated_by_", "updatedBy", PgBeanProcessor.fromJson());
  });

  private static String select() {
    return "SELECT ta.*" +
        ", " + TaskforgeUserProvider.select("ta.created_by") + " created_by_" +
        ", " + TaskforgeUserProvider.select("ta.updated_by") + " updated_by_" +
        " FROM taskforge.task_execution ta";
  }

  public Long save(TaskExecution te) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", te.getId());
    ssb.property("task_id", te.getTaskId());
    ssb.property("period_start", te.getPeriod() == null ? null : te.getPeriod().getLower());
    ssb.property("period_end", te.getPeriod() == null ? null : te.getPeriod().getUpper());
    ssb.property("duration", "?::interval", te.getDuration() == null ? null : te.getDuration().asString());
    ssb.jsonProperty("performer", te.getPerformer());
    if (te.getId() == null) { // only on insert
      ssb.property("created_by", userProvider.getReferenceId(te.getCreatedBy()));
      ssb.property("created_at", te.getCreatedAt());
    }
    ssb.property("updated_by", te.getUpdatedAt());
    ssb.property("updated_by", te.getUpdatedBy());

    SqlBuilder sb = ssb.buildSave("taskforge.task_execution", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public TaskExecution load(Long id) {
    String sql = select() + " FROM taskforge.task_execution te WHERE te.sys_status = 'A' where te.id = ?";
    return getBean(sql, bp, id);
  }

  public List<TaskExecution> loadAll(Long taskId) {
    String sql = select() + " FROM taskforge.task_execution te WHERE te.sys_status = 'A' where te.task_id = ?";
    return getBeans(sql, bp, taskId);
  }

}
