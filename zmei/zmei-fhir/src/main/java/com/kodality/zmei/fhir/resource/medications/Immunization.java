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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Immunization extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<Reference> basedOn;
  private String status;
  private CodeableConcept statusReason;
  private CodeableConcept vaccineCode;
  private CodeableReference administeredProduct;
  private CodeableReference manufacturer;
  private String lotNumber;
  private LocalDate expirationDate;
  private Reference patient;
  private Reference encounter;
  private List<Reference> supportingInformation;
  private OffsetDateTime occurrenceDateTime;
  private String occurrenceString;
  private Boolean primarySource;
  private CodeableReference informationSource;
  private Reference location;
  private CodeableConcept site;
  private CodeableConcept route;
  private Quantity doseQuantity;
  private List<ImmunizationPerformer> performer;
  private List<Annotation> note;
  private List<CodeableReference> reason;
  private Boolean isSubpotent;
  private List<CodeableConcept> subpotentReason;
  private List<ImmunizationProgramEligibility> programEligibility;
  private CodeableConcept fundingSource;
  private List<ImmunizationReaction> reaction;
  private List<ImmunizationProtocolApplied> protocolApplied;

  public Immunization() {
    super(ResourceType.immunization);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImmunizationPerformer extends BackboneElement {
    private CodeableConcept function;
    private Reference actor;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImmunizationProgramEligibility extends BackboneElement {
    private CodeableConcept program;
    private CodeableConcept programStatus;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImmunizationReaction extends BackboneElement {
    private OffsetDateTime date;
    private CodeableReference manifestation;
    private Boolean reported;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImmunizationProtocolApplied extends BackboneElement {
    private String series;
    private Reference authority;
    private List<CodeableConcept> targetDisease;
    private String doseNumber;
    private String seriesDoses;
  }
}
