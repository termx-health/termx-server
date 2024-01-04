package com.kodality.termx.sys.space.diff;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SpaceDiff {
  private List<SpaceDiffItem> items;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpaceDiffItem {
    private Long id;
    private String resourceId;
    private String resourceType;
    private String resourceServer;
    private boolean upToDate;
  }
}
