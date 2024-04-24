package com.kodality.termx.ts.valueset;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetVersionReference {
  private Long id;
  private String valueSet;
  private String version;
}
