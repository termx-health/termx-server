package com.kodality.zmei.fhir.resource.entities;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Address;
import com.kodality.zmei.fhir.datatypes.Availability;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ExtendedContactDetail;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.VirtualServiceDetail;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Location extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private Coding operationalStatus;
  private String name;
  private List<String> alias;
  private String description;
  private String mode;
  private List<CodeableConcept> type;
  private List<ExtendedContactDetail> contact;
  private Address address;
  private CodeableConcept form;
  private LocationPosition position;
  private Reference managingOrganization;
  private Reference partOf;
  private List<CodeableConcept> characteristic;
  private List<Availability> hoursOfOperations;
  private List<VirtualServiceDetail> virtualService;
  private List<Reference> endpoint;


  public Location() {
    super(ResourceType.location);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class LocationPosition extends BackboneElement {
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class LocationHoursOfOperation extends BackboneElement {
    private List<String> daysOfWeek;
    private Boolean allDay;
    private LocalTime openingTime;
    private LocalTime closingTime;

  }

}
