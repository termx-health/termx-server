package com.kodality.termserver.valueset;

import com.kodality.termserver.codesystem.EntityProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetRuleSet {
  private OffsetDateTime lockedDate;
  private Boolean inactive;
  private List<ValueSetRule> includeRules;
  private List<ValueSetRule> excludeRules;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetRule {
    private String codeSystem;
    private String codeSystemVersion;
    private List<ValueSetConcept> concepts;
    private List<ValueSetRuleFilter> filters;
    private String valueSet;
    private String valueSetVersion;

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class ValueSetRuleFilter {
      private EntityProperty property;
      private String operator;
      private String value;
    }
  }
}
