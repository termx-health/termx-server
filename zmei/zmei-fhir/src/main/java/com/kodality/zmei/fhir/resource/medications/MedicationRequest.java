package com.kodality.zmei.fhir.resource.medications;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Duration;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
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
public class MedicationRequest extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<Reference> basedOn;
  private Reference priorPrescription;
  private Identifier groupIdentifier;
  private String status;
  private CodeableConcept statusReason;
  private OffsetDateTime statusChanged;
  private String intent;
  private List<CodeableConcept> category;
  private String priority;
  private Boolean doNotPerform;
  private CodeableReference medication;
  private Reference subject;
  private List<Reference> informationSource;
  private Reference encounter;
  private List<Reference> supportingInformation;
  private OffsetDateTime authoredOn;
  private Reference requester;
  private Boolean reported;
  private CodeableConcept performerType;
  private List<Reference> performer;
  private List<CodeableReference> device;
  private Reference recorder;
  private List<CodeableReference> reason;
  private CodeableConcept courseOfTherapyType;
  private List<String> instantiatesCanonical;
  private List<String> instantiatesUri;
  private List<Reference> insurance;
  private List<Annotation> note;
  private String renderedDosageInstruction;
  private Period effectiveDosePeriod;
  private List<Dosage> dosageInstruction;
  private MedicationRequestDispenseRequest dispenseRequest;
  private MedicationRequestSubstitution substitution;
  private List<Reference> detectedIssue;
  private List<Reference> eventHistory;

  public MedicationRequest() {
    super(ResourceType.medicationRequest);
  }


  @Getter
  @Setter
  @Accessors(chain = true)
  private static class MedicationRequestDispenseRequest extends BackboneElement {
    private MedicationRequestInitialFill initialFill;
    private Duration dispenseInterval;
    private Period validityPeriod;
    private Integer numberOfRepeatsAllowed;
    private Quantity quantity;
    private Duration expectedSupplyDuration;
    private Reference dispenser;
    private List<Annotation> dispenserInstruction;
    private CodeableConcept doseAdministrationAid;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  private static class MedicationRequestInitialFill extends BackboneElement {
    private Quantity quantity;
    private Duration duration;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  private static class MedicationRequestSubstitution extends BackboneElement {
    private Boolean allowedBoolean;
    private CodeableConcept allowedCodeableConcept;
    private CodeableConcept reason;
  }
}
