package com.kodality.termserver.ts.project.diff;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ProjectDiff {
  private List<ProjectDiffItem> items;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProjectDiffItem {
    private String resourceId;
    private String resourceType;
    private String resourceServer;
    private boolean upToDate;
  }
}
