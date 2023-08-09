package com.kodality.termx.terminology.codesystem.entitypropertysummary;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CodeSystemEntityPropertySummary {
  private List<CodeSystemEntityPropertySummaryItem> items;

  @Getter
  @Setter
  public static class CodeSystemEntityPropertySummaryItem {
    private Long propertyId;
    private String propertyName;
    private Integer conceptCnt;
    private Integer propCnt;
    private List<Object> propList;
  }
}
