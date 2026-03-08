package org.termx.taskforge.task.readlog;

import com.kodality.commons.db.repo.BaseRepository;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;

@Singleton
public class TaskReadLogRepository extends BaseRepository {

  public void upsert(Long taskId, String userId) {
    String sql = """
        INSERT INTO taskforge.task_read_log (task_id, user_id, last_opened_time)
        VALUES (?, ?, ?)
        ON CONFLICT ON CONSTRAINT task_read_log_ukey
        DO UPDATE SET last_opened_time = excluded.last_opened_time
        """;
    jdbcTemplate.update(sql, taskId, userId, OffsetDateTime.now());
  }

  public OffsetDateTime getLastOpenedTime(Long taskId, String userId) {
    String sql = "SELECT last_opened_time FROM taskforge.task_read_log WHERE task_id = ? AND user_id = ? AND sys_status = 'A'";
    return jdbcTemplate.queryForObject(sql, OffsetDateTime.class, taskId, userId);
  }
}
