package org.termx.taskforge.task.execution;

import com.kodality.commons.model.Interval;
import com.kodality.commons.util.range.OffsetDateTimeRange;
import org.termx.taskforge.user.TaskforgeUser;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskExecution {
  private Long id;
  private Long taskId;
  private OffsetDateTimeRange period;
  private Interval duration;
  private Object performer;
  private TaskforgeUser updatedBy;
  private OffsetDateTime updatedAt;
  private TaskforgeUser createdBy;
  private OffsetDateTime createdAt;
}
