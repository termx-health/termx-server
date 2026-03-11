package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class UsageContext extends Element {
  private Coding code;
  private CodeableConcept valueCodeableConcept;
  private Quantity valueQuantity;
  private Range valueRange;
  private Reference valueReference;
}
