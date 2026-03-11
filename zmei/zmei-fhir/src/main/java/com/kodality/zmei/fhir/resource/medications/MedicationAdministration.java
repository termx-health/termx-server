package com.kodality.zmei.fhir.resource.medications;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Ratio;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.Timing;
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
public class MedicationAdministration extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<Reference> basedOf;
  private List<Reference> partOf;
  private String status;
  private List<CodeableConcept> statusReason;
  private List<CodeableConcept> category;
  private CodeableReference medication;
  private Reference subject;
  private Reference context;
  private List<Reference> supportingInformation;
  private OffsetDateTime occurenceDateTime;
  private Period occurencePeriod;
  private Timing occurenceTiming;
  private OffsetDateTime recorded;
  private Boolean isSubPotent;
  private CodeableConcept subPotentReason;
  private List<MedicationAdministrationPerformer> performer;
  private List<CodeableReference> reason;
  private Reference request;
  private List<CodeableReference> device;
  private List<Annotation> note;
  private MedicationAdministrationDosage dosage;
  private List<Reference> eventHistory;


  public MedicationAdministration() {
    super(ResourceType.medicationAdministration);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class MedicationAdministrationPerformer extends BackboneElement {
    private CodeableConcept function;
    private Reference actor;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class MedicationAdministrationDosage extends BackboneElement {
    private String text;
    private CodeableConcept site;
    private CodeableConcept route;
    private CodeableConcept method;
    private Quantity dose;
    private Ratio rateRatio;
    private Quantity rateQuantity;
  }
}
