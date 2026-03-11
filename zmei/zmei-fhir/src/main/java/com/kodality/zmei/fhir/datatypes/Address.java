package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Address extends Element {
  private String use;
  private String type;
  private String text;
  private List<String> line;
  private String city;
  private String district;
  private String state;
  private String postalCode;
  private String country;
  private Period period;
}
