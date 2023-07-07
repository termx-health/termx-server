package com.kodality.termx.task;

import com.kodality.commons.model.QueryParams;
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
}
