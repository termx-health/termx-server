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
import com.kodality.zmei.fhir.resource.ResourceType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static java.util.stream.Collectors.joining;

@Getter
@Setter
@Accessors(chain = true)
public class Practitioner extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Boolean active;
  private List<HumanName> name;
  private List<ContactPoint> telecom;
  private String gender;
  private LocalDate birthDate;
  private Boolean deceasedBoolean;
  private OffsetDateTime deceasedDateTime;
  private List<Address> address;
  private List<Attachment> photo;
  private List<PractitionerQualification> qualification;
  private List<PractitionerCommunication> communication;

  public Practitioner() {
    super(ResourceType.practitioner);
  }

  public String formattedName() {
    return name == null ? "" : name.stream().map(HumanName::formattedName).collect(joining("; "));
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class PractitionerQualification extends BackboneElement {
    private List<Identifier> identifier;
    private CodeableConcept code;
    private Period period;
    private Reference issuer;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class PractitionerCommunication extends BackboneElement {
    private CodeableConcept language;
    private Boolean preferred;
  }
}
