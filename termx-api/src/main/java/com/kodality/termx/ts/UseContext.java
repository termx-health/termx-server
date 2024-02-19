package com.kodality.termx.ts;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class UseContext {
  private String type;
  private String value;
}
