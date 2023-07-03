package com.kodality.termx.atcest.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AtcEst {
  @JsonProperty(value = "ATC kood")
  private String code;
  @JsonProperty(value = "Nimi")
  private String name;
}
