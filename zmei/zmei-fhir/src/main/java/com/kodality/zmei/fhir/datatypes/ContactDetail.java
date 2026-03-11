package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ContactDetail extends Element {
  private String name;
  private List<ContactPoint> telecom;
}
