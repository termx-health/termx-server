package com.kodality.zmei.fhir.resource.summary;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class DetectedIssue extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private List<CodeableConcept> category;
  private CodeableConcept code;
  private String severity;
  private Reference subject;
  private Reference encounter;
  private OffsetDateTime identifiedDateTime;
  private Period identifiedPeriod;
  private Reference author;
  private List<Reference> implicated;
  private List<DetectedIssueEvidence> evidence;
  private String detail;
  private String reference;
  private List<DetectedIssueMitigation> mitigation;

  public DetectedIssue() {
    super(ResourceType.detectedIssue);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DetectedIssueEvidence extends BackboneElement {
    private List<CodeableConcept> code;
    private List<Reference> detail;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DetectedIssueMitigation extends BackboneElement {
    private CodeableConcept action;
    private OffsetDateTime date;
    private Reference author;
    private List<Annotation> note;
  }
}
