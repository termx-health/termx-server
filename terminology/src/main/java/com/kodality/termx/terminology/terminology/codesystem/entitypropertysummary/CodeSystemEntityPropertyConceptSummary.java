package com.kodality.termx.terminology.terminology.codesystem.entitypropertysummary;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CodeSystemEntityPropertyConceptSummary {
  private List<CodeSystemEntityPropertyConceptSummaryItem> items;

  @Getter
  @Setter
  public static class CodeSystemEntityPropertyConceptSummaryItem {
    private String propertyCode;
    private Integer conceptCnt;
    private List<Long> conceptIds;
  }
}
