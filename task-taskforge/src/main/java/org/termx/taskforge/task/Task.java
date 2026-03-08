package org.termx.taskforge.task;

import org.termx.taskforge.user.TaskforgeUser;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class Task {
  private Long id;
  @NotNull
  private Long projectId;
  @NotNull
  private Long workflowId;
  private Long parentId;
  private String number;
  private String type;
  private String status;
  private String businessStatus;
  private String priority;
  @Valid
  private TaskforgeUser createdBy;
  private OffsetDateTime createdAt;
  @Valid
  private TaskforgeUser assignee;
  private TaskforgeUser updatedBy;
  private OffsetDateTime updatedAt;
  @NotNull
  private String title;
  private String content;
  private List<TaskContextItem> context;
  private List<TaskAttachment> attachments;

  @Getter
  @Setter
  public static class TaskAttachment {
    private String fileId;
    private String fileName;
    private String description;

    private String attachmentKey;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class TaskContextItem {
    private String type;
    private Object id;
  }

  public interface TaskStatus {
    String draft = "draft";
    String requested = "requested";
    String received = "received";
    String accepted = "accepted";
    String rejected = "rejected";
    String ready = "ready";
    String cancelled = "cancelled";
    String in_progress = "in-progress";
    String on_hold = "on-hold";
    String failed = "failed";
    String completed = "completed";
    String error = "entered-in-error";
  }

  public interface TaskPriority {
    String routine = "routine";
    String urgent = "urgent";
    String asap = "asap";
    String stat = "stat";
  }
}
