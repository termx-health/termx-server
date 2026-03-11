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
public class AllergyIntolerance extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private CodeableConcept clinicalStatus;
  private CodeableConcept verificationStatus;
  private CodeableConcept type;
  private List<String> category;
  private String criticality;
  private CodeableConcept code;
  private Reference patient;
  private Reference encounter;
  private OffsetDateTime onsetDateTime;
  private Age onsetAge;
  private Period onsetPeriod;
  private Range onsetRange;
  private String onsetString;
  private OffsetDateTime recordedDate;
  private List<AllergyIntoleranceParticipant> participant;
  private OffsetDateTime lastOccurence;
  private List<Annotation> note;
  private List<AllergyIntoleranceReaction> reaction;


  public AllergyIntolerance() {
    super(ResourceType.allergyIntolerance);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class AllergyIntoleranceParticipant extends BackboneElement {
    private CodeableConcept function;
    private Reference actor;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class AllergyIntoleranceReaction extends BackboneElement {
    private CodeableConcept substance;
    private List<CodeableReference> manifestation;
    private String description;
    private OffsetDateTime onSet;
    private String severity;
    private CodeableConcept exposureRoute;
    private List<Annotation> note;
  }
}
