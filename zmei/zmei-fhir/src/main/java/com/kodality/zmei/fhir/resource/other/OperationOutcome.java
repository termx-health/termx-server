package com.kodality.zmei.fhir.resource.other;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.resource.DomainResource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class OperationOutcome extends DomainResource {
  private List<OperationOutcomeIssue> issue = new ArrayList<>();

  public OperationOutcome() {
    super("OperationOutcome");
  }

  public OperationOutcome(OperationOutcomeIssue... issue) {
    this();
    this.issue = Arrays.asList(issue);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class OperationOutcomeIssue extends BackboneElement {
    private String severity;
    private String code;
    private CodeableConcept details;
    private String diagnostics;
    private List<String> location;
    private List<String> expression;

  }
}
