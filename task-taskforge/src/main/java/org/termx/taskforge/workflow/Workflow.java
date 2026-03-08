package org.termx.taskforge.workflow;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Workflow {
  private Long id;
  private String taskType;
  private List<WorkflowTransition> transitions;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class WorkflowTransition {
    private String from;
    private String to;
  }
}
