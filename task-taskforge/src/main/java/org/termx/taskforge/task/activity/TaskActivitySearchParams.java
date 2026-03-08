package org.termx.taskforge.task.activity;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TaskActivitySearchParams {
  private String ids;
  private String taskIds;
  private String noteContains;
  private String context; //type1|id1,type2|id2
  private String updatedBy;
  private String updatedGe;
  private String updatedLe;

  private List<String> sort;

  public interface Ordering {
    String author = "author";
    String updated = "updated";
  }
}
