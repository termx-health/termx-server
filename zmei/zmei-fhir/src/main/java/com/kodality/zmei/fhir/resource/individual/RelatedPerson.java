package com.kodality.zmei.fhir.resource.individual;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Address;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.HumanName;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class RelatedPerson extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Boolean active;
  private Reference patient;
  private List<CodeableConcept> relationship;
  private List<HumanName> name;
  private List<ContactPoint> telecom;
  private String gender;
  private LocalDate birthDate;
  private List<Address> address;
  private List<Attachment> photo;
  private Period period;
  private List<RelatedPersonCommunication> communication;

  public RelatedPerson() {
    super("RelatedPerson");
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class RelatedPersonCommunication extends BackboneElement {
    private CodeableConcept language;
    private Boolean preferred;
  }

}
