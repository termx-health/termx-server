package com.kodality.termx.sys.checklist;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Checklist {
  private Long id;
  private ChecklistRule rule;
  private String resourceType;
  private String resourceId;

  private List<ChecklistWhitelist> whitelist;
  private List<ChecklistAssertion> assertions;

  @Getter
  @Setter
  public static class ChecklistWhitelist {
    private Long id;
    private String resourceType;
    private String resourceId;
    private String resourceName;
  }
}
