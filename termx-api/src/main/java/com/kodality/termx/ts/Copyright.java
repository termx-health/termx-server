package com.kodality.termx.ts;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Copyright {
  private String holder;
  private String jurisdiction;
  private String statement;
}
