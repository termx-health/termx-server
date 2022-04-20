package com.kodality.termserver.commons.model.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class Identifier {
  private String system;
  private String value;

  public Identifier() {}

  public Identifier(String system, String value) {
    this.system = system;
    this.value = value;
  }

  public String asPipe() {
    return (system == null ? "" : system) + "|" + value;
  }
}
