package com.kodality.termx.task;

import com.kodality.commons.model.CodeName;
import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class Task {
  private String number;
  private String workflow;
  private CodeName project;
  private String type;
  private String status;
  private String priority;
  private String createdBy;
  private OffsetDateTime createdAt;
  private String assignee;
  private String updatedBy;
  private OffsetDateTime updatedAt;

  private String title;
  private String content;

  private List<TaskContextItem> context;
  private List<TaskActivity> activities;


  @Getter
  @Setter
  @Accessors(chain = true)
  public static class TaskContextItem {
    private String type;
    private Object id;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class TaskActivity {
    private String id;
    private String note;
    private Map<String, TaskActivityTransition> transition;
    private String updatedBy;
    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class TaskActivityTransition {
      private String from;
      private String to;
    }
  }
}
