package com.kodality.zmei.fhir.resource.entities;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.Availability;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.ExtendedContactDetail;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class HealthcareService extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Boolean active;
  private Reference providedBy;
  private List<Reference> offeredIn;
  private List<CodeableConcept> category;
  private List<CodeableConcept> type;
  private List<CodeableConcept> speciality;
  private List<Reference> location;
  private String name;
  private String comment;
  private String extraDetails;
  private Attachment photo;
  private List<ExtendedContactDetail> contact;
  private List<Reference> coverageArea;
  private List<CodeableConcept> serviceProvisionCode;
  private List<HealthcareServiceEligibility> eligibility;
  private List<CodeableConcept> program;
  private List<CodeableConcept> characteristic;
  private List<CodeableConcept> communication;
  private List<CodeableConcept> referralMethod;
  private Boolean appointmentRequired;
  private List<Availability> availability;
  private List<Reference> endpoint;

  public HealthcareService() {
    super(ResourceType.healthcareService);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  private static class HealthcareServiceEligibility extends BackboneElement {
    private CodeableConcept code;
    private String comment;
  }
}
