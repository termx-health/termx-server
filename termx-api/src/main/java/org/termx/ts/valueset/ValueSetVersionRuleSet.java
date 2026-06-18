package org.termx.ts.valueset;

import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.property.PropertyReference;
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
  /**
   * Tri-state mirror of FHIR {@code ValueSet.compose.inactive} (nullable on purpose):
   * {@code null}/absent → server default (inactive concepts included, rendered with {@code inactive=true},
   * filtered only at render time by {@code activeOnly}); {@code TRUE} → include inactive; {@code FALSE} →
   * explicitly exclude inactive from the expansion.
   */
  private Boolean inactive;
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
