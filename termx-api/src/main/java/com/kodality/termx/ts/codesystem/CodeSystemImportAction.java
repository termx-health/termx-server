package com.kodality.termx.ts.codesystem;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemImportAction {
  private boolean activate;
  private boolean retire;
  private boolean cleanRun;
  private boolean cleanConceptRun;
  private String spaceToAdd; // spaceCode|packageCode

  private boolean generateValueSet;
  private List<String> valueSetProperties;
}
