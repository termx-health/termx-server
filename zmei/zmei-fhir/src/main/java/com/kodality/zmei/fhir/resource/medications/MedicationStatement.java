package com.kodality.zmei.fhir.resource.medications;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
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
public class MedicationStatement extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<Reference> partOf;
  private String status;
  private List<CodeableConcept> category;
  private CodeableReference medication;
  private Reference subject;
  private Reference context;
  private OffsetDateTime effectiveDateTime;
  private Period effectivePeriod;
  private Timing effectiveTiming;
  private OffsetDateTime dateAsserted;
  private List<Reference> informationSource;
  private List<Reference> derivedFrom;
  private List<CodeableReference> reason;
  private List<Annotation> note;
  private List<Reference> relatedClinicalInformation;
  private String relatedDosageInformation;
  private List<Dosage> dosage;
  private MedicationStatementAdherence adherence;

  public MedicationStatement() {
    super(ResourceType.medicationStatement);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class MedicationStatementAdherence extends BackboneElement {
    private CodeableConcept code;
    private CodeableConcept reason;
  }

}
