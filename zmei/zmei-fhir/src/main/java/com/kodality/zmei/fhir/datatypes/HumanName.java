package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class HumanName extends Element {
  private String use;
  private String text;
  private String family;
  private List<String> given;
  private List<String> prefix;
  private List<String> suffix;
  private Period period;

  public String formattedName() {
    return family + (given == null || given.isEmpty()? "" : ", " + String.join(" ", given));
  }
}
