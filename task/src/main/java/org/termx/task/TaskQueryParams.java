package org.termx.task;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TaskQueryParams extends QueryParams {
  private String statuses;
  private String statusesNe;
  private String priorities;
  private String types;
  private String textContains;
  private String createdGe;
  private String createdLe;
  private String createdBy;
  private String modifiedGe;
  private String modifiedLe;
  private String assignees;
  private String context;
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
}
