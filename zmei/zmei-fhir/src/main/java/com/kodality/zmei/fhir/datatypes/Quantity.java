package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@ToString
public class Quantity extends Element {
  private BigDecimal value;
  private String comparator;
  private String unit;
  private String system;
  private String code;
}
