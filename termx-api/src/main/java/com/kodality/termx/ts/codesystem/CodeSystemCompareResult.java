package com.kodality.termx.ts.codesystem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kodality.termx.ts.PublicationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class CodeSystemCompareResult {
  private List<String> added = new ArrayList<>();
  private List<String> deleted = new ArrayList<>();
  private List<CodeSystemCompareResultChange> changed = new ArrayList<>();

  public List<String> affectedCodes() {
    List<String> codes = new ArrayList<>();
    codes.addAll(this.added);
    codes.addAll(this.deleted);
    codes.addAll(this.changed.stream().map(CodeSystemCompareResultChange::getCode).toList());
    return codes;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemCompareResultChange {
    private String code;
    private CodeSystemCompareResultDiff diff;

    @JsonIgnore
    public boolean retired() {
      return PublicationStatus.retired.equals(this.diff.getMew().getStatus());
    }

    @JsonIgnore
    public boolean contentChanged() {
      return Stream.of(
              this.diff.getMew().getDesignations(),
              this.diff.getMew().getProperties())
          .anyMatch(Objects::nonNull);
    }
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
    private String description;
    private List<String> designations;
    private List<String> properties;
  }
}
