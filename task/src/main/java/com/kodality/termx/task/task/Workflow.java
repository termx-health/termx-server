package com.kodality.termx.task.task;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Workflow {
  private String code;
  private List<WorkflowTransition> transitions;

  @Getter
  @Setter
  public static class WorkflowTransition {
    private String from;
    private String to;
  }
}
