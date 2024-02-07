package com.kodality.termx.sys.checklist;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ChecklistValidationRequest {
  private String resourceType;
  private String resourceId;
  private String resourceVersion;
  private Long checklistId;
  private String ruleTarget;
}
