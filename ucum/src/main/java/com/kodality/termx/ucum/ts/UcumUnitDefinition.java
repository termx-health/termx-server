package com.kodality.termx.ucum.ts;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class UcumUnitDefinition {
  private String code;
  private String kind;
  private String property;
  private List<String> names;
}
