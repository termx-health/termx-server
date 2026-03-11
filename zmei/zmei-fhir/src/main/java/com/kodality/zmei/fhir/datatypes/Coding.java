package com.kodality.zmei.fhir.datatypes;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kodality.zmei.fhir.Element;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@ToString
public class Coding extends Element {
  private String system;
  private String version;
  private String code;
  private String display;
  private Boolean userSelected;

  public Coding() {
  }

  public Coding(String code) {
    this.code = code;
  }

  public Coding(String system, String code) {
    this.system = system;
    this.code = code;
  }

  @JsonIgnore
  public boolean isSame(Coding coding) {
    return coding != null && isSame(coding.getSystem(), coding.getCode());
  }

  @JsonIgnore
  public boolean isSame(String system, String code) {
    return Objects.equals(system, this.system) && Objects.equals(code, this.code);
  }
}
