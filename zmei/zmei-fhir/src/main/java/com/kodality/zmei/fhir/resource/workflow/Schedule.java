package com.kodality.zmei.fhir.resource.workflow;

import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.util.Lists;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Schedule extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Boolean active;
  private List<CodeableConcept> serviceCategory;
  private List<CodeableReference> serviceType;
  private List<CodeableConcept> specialty;
  private String name;
  private List<Reference> actor;
  private Period planningHorizon;
  private String comment;

  public Schedule() {
    super(ResourceType.schedule);
  }

  public Schedule addIdentifier(Identifier o) {
    this.identifier = Lists.add(this.identifier, o);
    return this;
  }

  public Schedule addServiceType(CodeableReference o) {
    this.serviceType = Lists.add(this.serviceType, o);
    return this;
  }

  public Schedule addSpecialty(CodeableConcept o) {
    this.specialty = Lists.add(this.specialty, o);
    return this;
  }

  public Schedule addActor(Reference o) {
    this.actor = Lists.add(this.actor, o);
    return this;
  }
}
