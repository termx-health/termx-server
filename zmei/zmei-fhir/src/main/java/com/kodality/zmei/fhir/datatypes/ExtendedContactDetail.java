package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ExtendedContactDetail extends Element {
  private CodeableConcept purpose;
  private List<HumanName> name;
  private List<ContactPoint> telecom;
  private Address address;
  private Reference organization;
  private Period period;
}
