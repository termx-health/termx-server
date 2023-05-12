package com.kodality.termserver.terminology.codesystem.compare;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class CodeSystemCompareResult {
  private List<String> added = new ArrayList<>();
  private List<String> deleted = new ArrayList<>();
  private List<CodeSystemCompareResultChange> changed = new ArrayList<>();

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemCompareResultChange {
    private String code;
    private CodeSystemCompareResultDiff diff;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemCompareResultDiff {
    private CodeSystemCompareResultDiffItem old;
    private CodeSystemCompareResultDiffItem mew;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemCompareResultDiffItem {
    private String status;
    private List<String> properties;
  }
}
