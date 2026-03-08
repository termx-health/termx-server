package org.termx.taskforge.workflow;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class WorkflowSearchParams extends QueryParams {
  private String ids;
  private String projectIds;
  private String projectCodes;
  private String types;
}
