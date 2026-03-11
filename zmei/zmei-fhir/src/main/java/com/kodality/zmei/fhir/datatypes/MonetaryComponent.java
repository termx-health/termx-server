package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MonetaryComponent extends Element {
  private String type;
  private CodeableConcept code;
  private BigDecimal factor;
  private Money amount;
}
