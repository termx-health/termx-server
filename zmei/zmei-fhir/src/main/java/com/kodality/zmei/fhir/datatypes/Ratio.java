package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Ratio extends Element {
  private Quantity numerator;
  private Quantity denominator;
}
