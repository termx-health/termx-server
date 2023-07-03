package com.kodality.termx.sys.lorque;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class LorqueProgress {
  private Integer value;
  private Integer max;
}
