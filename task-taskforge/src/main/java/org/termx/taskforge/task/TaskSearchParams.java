package org.termx.taskforge.task;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TaskSearchParams extends QueryParams {
  private String ids;
  private String statuses;
  private String statusesNe;
  private String projectIds;
  private String priorities;
  private String types;
  private String textContains;
  private String createdGe;
  private String createdLe;
  private String createdBy;
  private String modifiedGe;
  private String modifiedLe;
  private String assignees;
  private String context; //type1|id1,type2|id2
  private String createdByOrAssignee;
  private List<String> permittedContexts; // type|id pairs the user has access to; null means all
  private Boolean unseenChanges;
  private String unseenChangesUser;

  public interface Ordering {
    String number = "number";
    String title = "title";
    String type = "type";
    String status = "status";
    String author = "author";
    String assignee = "assignee";
    String created = "created";
    String updated = "updated";
  }
}
