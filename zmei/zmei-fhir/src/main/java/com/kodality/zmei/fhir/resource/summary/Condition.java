package com.kodality.zmei.fhir.resource.summary;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Age;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Range;
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
public class Condition extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private CodeableConcept clinicalStatus;
  private CodeableConcept verificationStatus;
  private List<CodeableConcept> category;
  private CodeableConcept severity;
  private CodeableConcept code;
  private List<CodeableConcept> bodySite;
  private Reference subject;
  private Reference encounter;
  private OffsetDateTime onsetDateTime;
  private Age onsetAge;
  private Period onsetPeriod;
  private Range onsetRange;
  private String onsetString;
  private OffsetDateTime abatementDateTime;
  private Age abatementAge;
  private Period abatementPeriod;
  private Range abatementRange;
  private String abatementString;
  private OffsetDateTime recordedDate;
  private List<ConditionParticipant> participant;
  private List<ConditionStage> stage;
  private List<CodeableReference> evidence;
  private List<Annotation> note;

  public Condition() {
    super(ResourceType.condition);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConditionParticipant extends BackboneElement {
    private CodeableConcept function;
    private Reference actor;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  private static class ConditionStage extends BackboneElement {
    private CodeableConcept summary;
    private List<Reference> assessment;
    private CodeableConcept type;
  }
}
