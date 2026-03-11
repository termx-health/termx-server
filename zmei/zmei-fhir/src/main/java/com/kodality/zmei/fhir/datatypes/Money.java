package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Money extends Element {
  private BigDecimal value;
  private String currency;
}
