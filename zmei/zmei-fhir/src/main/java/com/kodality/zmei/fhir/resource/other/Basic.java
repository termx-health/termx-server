package com.kodality.zmei.fhir.resource.other;

import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Identifier;
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
public class Basic extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private CodeableConcept code;
  private Reference subject;
  private OffsetDateTime created;
  private Reference author;

  public Basic() {
    super(ResourceType.basic);
  }
}
