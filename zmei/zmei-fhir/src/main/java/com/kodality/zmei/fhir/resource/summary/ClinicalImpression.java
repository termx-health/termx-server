package com.kodality.zmei.fhir.resource.summary;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
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
public class ClinicalImpression extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private CodeableConcept statusReason;
  private String description;
  private Reference subject;
  private Reference encounter;
  private OffsetDateTime effectiveDateTime;
  private Period effectivePeriod;
  private OffsetDateTime date;
  private Reference performer;
  private Reference previous;
  private List<Reference> problem;
  private CodeableConcept changePattern;
  private List<String> protocol;
  private String summary;
  private List<ClinicalImpressionFinding> finding;
  private List<CodeableConcept> prognosisCodeableConcept;
  private List<Reference> prognosisReference;
  private List<Reference> supportingInfo;
  private List<Annotation> note;

  public ClinicalImpression() {
    super(ResourceType.clinicalImpression);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ClinicalImpressionFinding extends BackboneElement {
    private CodeableReference item;
    private String basis;
  }
}
