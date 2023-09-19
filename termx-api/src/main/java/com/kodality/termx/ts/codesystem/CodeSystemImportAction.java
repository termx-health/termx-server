package com.kodality.termx.ts.codesystem;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemImportAction {
  private boolean activate;
  private boolean retire;
  private boolean generateValueSet;
  private boolean cleanRun;
  private boolean cleanConceptRun;
}
