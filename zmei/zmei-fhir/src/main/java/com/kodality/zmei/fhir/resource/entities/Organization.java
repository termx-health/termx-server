package com.kodality.zmei.fhir.resource.entities;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
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
public class Organization extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Boolean active;
  private List<CodeableConcept> type;
  private String name;
  private List<String> alias;
  private String description;
  private List<ExtendedContactDetail> contact;
  private Reference partOf;
  private List<Reference> endpoint;
  private List<OrganizationQualification> qualification;

  public Organization() {
    super(ResourceType.organization);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class OrganizationQualification extends BackboneElement {
    private Identifier identifier;
    private CodeableConcept code;
    private Period period;
    private Reference issuer;
  }
}
