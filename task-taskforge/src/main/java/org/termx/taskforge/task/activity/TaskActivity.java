package org.termx.taskforge.task.activity;

import org.termx.taskforge.user.TaskforgeUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class TaskActivity {
  private Long id;
  @NotNull
  private Long taskId;
  private String note;
  private Map<String, TaskActivityTransition> transition;
  private List<TaskActivityContextItem> context;
  private TaskforgeUser updatedBy;
  private OffsetDateTime updatedAt;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class TaskActivityTransition {
    private String from;
    private String to;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class TaskActivityContextItem {
    private String type;
    private Object id;
  }
}
