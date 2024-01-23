package com.kodality.termx.sys.checklist;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class ChecklistAssertion {
  private Long id;
  private boolean passed;
  private String executor;
  private OffsetDateTime executionDate;
  private List<ChecklistAssertionError> errors;

  private Long checklistId;
  private Long ruleId;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ChecklistAssertionError {
    private String error;
    private String resourceId;
    private String resourceType;
  }
}
