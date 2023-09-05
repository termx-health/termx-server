package com.kodality.termx.ts.mapset;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetImportAction {
  private boolean activate;
  private boolean cleanRun;
  private boolean cleanAssociationRun;
}
