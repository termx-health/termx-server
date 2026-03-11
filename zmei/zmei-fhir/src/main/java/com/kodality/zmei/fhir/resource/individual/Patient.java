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
import com.kodality.zmei.fhir.util.Lists;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static java.util.stream.Collectors.joining;

@Getter
@Setter
@Accessors(chain = true)
public class Patient extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Boolean active;
  private List<HumanName> name;
  private List<ContactPoint> telecom;
  private String gender;
  private LocalDate birthDate;
  private Boolean deceasedBoolean;
  private OffsetDateTime deceasedDateTime;
  private List<Address> address;
  private CodeableConcept maritalStatus;
  private Boolean multipleBirthBoolean;
  private Integer multipleBirthInteger;
  private List<Attachment> photo;
  private List<PatientContact> contact;
  private List<PatientCommunication> communication;
  private List<Reference> generalPractitioner;
  private Reference managingOrganization;
  private List<PatientLink> link;

  public Patient() {
    super(ResourceType.patient);
  }

  public Patient addIdentifier(Identifier o) {
    this.identifier = Lists.add(this.identifier, o);
    return this;
  }

  public String getName(String use) {
    if (use == null || name == null) {
      return "";
    }
    return name.stream().filter(n -> use.equals(n.getUse())).findFirst().map(name -> {
      String given = name.getGiven() == null ? null : name.getGiven().stream().collect(joining(" "));
      return Stream.of(name.getFamily(), given).filter(n -> n != null).collect(joining(", "));
    }).orElse("");
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class PatientCommunication extends BackboneElement {
    private CodeableConcept language;
    private Boolean preferred;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class PatientContact extends BackboneElement {
    private List<CodeableConcept> relationship;
    private HumanName name;
    private List<ContactPoint> telecom;
    private Address address;
    private String gender;
    private Reference organization;
    private Period period;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class PatientLink extends BackboneElement {
    private Reference other;
    private String type;
  }
}
