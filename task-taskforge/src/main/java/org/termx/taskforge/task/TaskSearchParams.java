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
  private Boolean unseenChanges;
  private String unseenChangesUser;
  private TaskVisibilityFilter visibilityFilter;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class TaskVisibilityFilter {
    private String username;                  // creator OR assignee match
    private List<String> publisherContexts;   // null = all contexts (wildcard publisher), empty = no publisher access
  }

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
