package com.kodality.zmei.fhir.resource.medications;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
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
public class MedicationDispense extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<Reference> basedOn;
  private List<Reference> partOf;
  private String status;
  private CodeableReference nonPerformedReason;
  private OffsetDateTime statusChanged;
  private List<CodeableConcept> category;
  private CodeableReference medication;
  private Reference subject;
  private Reference encounter;
  private List<Reference> supportingInformation;
  private List<MedicationDispensePerformer> performer;
  private Reference location;
  private List<Reference> authorizingPrescription;
  private CodeableConcept type;
  private Quantity quantity;
  private Quantity daysSupply;
  private OffsetDateTime recorder;
  private OffsetDateTime whenPrepared;
  private OffsetDateTime whenHandedOver;
  private Reference destination;
  private List<Reference> receiver;
  private List<Annotation> note;
  private String renderedDosageInstruction;
  private List<Dosage> dosageInstruction;
  private MedicationDispenseSubstitution substitution;
  private List<Reference> eventHistory;

  public MedicationDispense() {
    super(ResourceType.medicationDispense);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  private static class MedicationDispensePerformer extends BackboneElement {
    private CodeableConcept function;
    private Reference actor;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  private static class MedicationDispenseSubstitution extends BackboneElement {
    private Boolean wasSubstituted;
    private CodeableConcept type;
    private List<CodeableConcept> reason;
    private Reference responsibleParty;
  }
}
