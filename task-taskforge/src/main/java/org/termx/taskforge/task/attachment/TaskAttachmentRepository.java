package org.termx.taskforge.task.attachment;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import org.termx.taskforge.task.Task.TaskAttachment;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class TaskAttachmentRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(TaskAttachment.class);

  public List<TaskAttachment> loadAll(Long taskId) {
    String sql = "select * from taskforge.task_attachment where task_id = ? and sys_status = 'A'";
    return getBeans(sql, bp, taskId);
  }

  public void insert(Long taskId, TaskAttachment attachment) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("task_id", taskId);
    ssb.property("file_id", attachment.getFileId());
    ssb.property("file_name", attachment.getFileName());
    ssb.property("description", attachment.getDescription());
    SqlBuilder sb = ssb.buildInsert("taskforge.task_attachment", "id");
    jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public void retain(Long taskId, List<TaskAttachment> attachments) {
    SqlBuilder sb = new SqlBuilder("update taskforge.task_attachment SET sys_status = 'C' ");
    sb.append(" WHERE task_id = ?", taskId);
    sb.andNotIn("file_id", attachments, TaskAttachment::getFileId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

}
