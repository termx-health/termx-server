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
public class ValueSetVersionRuleSet {
  private Long id;
  private OffsetDateTime lockedDate;
  private Boolean inactive;
  private List<ValueSetVersionRule> rules;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetVersionRule {
    private Long id;
    private String type;
    private String codeSystem;
    private Long codeSystemVersionId;
    private List<ValueSetVersionConcept> concepts;
    private List<ValueSetRuleFilter> filters;
    private String valueSet;
    private Long valueSetVersionId;

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
