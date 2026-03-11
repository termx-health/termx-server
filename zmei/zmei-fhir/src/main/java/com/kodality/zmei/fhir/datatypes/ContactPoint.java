package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ContactPoint extends Element {
  private String system;
  private String value;
  private String use;
  private Integer rank;
  private Period period;
}
