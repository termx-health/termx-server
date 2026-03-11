package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Expression extends Element {
  private String description;
  private String name;
  private String language;
  private String expression;
  private String reference;
}
