package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeableReference extends Element {
  private CodeableConcept concept;
  private Reference reference;

}
