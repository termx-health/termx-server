package com.kodality.termx.sys.checklist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class ChecklistValidationRequest {
  private String resourceType;
  private String resourceId;
  private Long checklistId;
  private String ruleTarget;
}
