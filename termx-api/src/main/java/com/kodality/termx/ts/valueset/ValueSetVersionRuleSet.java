package com.kodality.termx.ts.valueset;

import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.property.PropertyReference;
import io.micronaut.core.annotation.Introspected;
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
  private boolean inactive;
  private List<ValueSetVersionRule> rules;

  @Getter
  @Setter
  @Introspected
  @Accessors(chain = true)
  public static class ValueSetVersionRule {
    private Long id;
    private String type;
    private String codeSystem;
    private CodeSystemVersionReference codeSystemVersion;
    private List<String> properties;
    private List<ValueSetVersionConcept> concepts;
    private List<ValueSetRuleFilter> filters;
    private String valueSet;
    private ValueSetVersionReference valueSetVersion;

    private String codeSystemUri;
    private String codeSystemBaseUri;
    private String valueSetUri;

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class ValueSetRuleFilter {
      private PropertyReference property;
      private String operator;
      private Object value;
    }
  }
}
