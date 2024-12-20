package com.kodality.termx.ts.valueset;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetImportAction {
  private boolean activate;
  private boolean retire;
  private boolean cleanRun;
  private String spaceToAdd; //spaceCode|packageCode
}
