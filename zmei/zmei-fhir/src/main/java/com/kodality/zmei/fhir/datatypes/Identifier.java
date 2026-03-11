package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Identifier extends Element {
  private String use;
  private CodeableConcept type;
  private String system;
  private String value;
  private Period period;
  private Reference assigner;


  public Identifier() {
  }

  public Identifier(String system, String value) {
    this.system = system;
    this.value = value;
  }
}
