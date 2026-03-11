package com.kodality.zmei.fhir.resource.individual;

import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Availability;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.ExtendedContactDetail;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
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
public class PractitionerRole extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Boolean active;
  private Period period;
  private Reference practitioner;
  private Reference organization;
  private List<CodeableConcept> code;
  private List<CodeableConcept> specialty;
  private List<Reference> location;
  private List<Reference> healthcareService;
  private List<ExtendedContactDetail> contact;
  private List<CodeableConcept> characteristic;
  private List<CodeableConcept> communication;
  private List<Availability> availability;
  private List<Reference> endpoint;

  public PractitionerRole() {
    super(ResourceType.practitionerRole);
  }
}
