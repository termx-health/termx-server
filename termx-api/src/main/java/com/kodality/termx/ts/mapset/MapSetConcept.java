package com.kodality.termx.ts.mapset;

import com.kodality.termx.ts.codesystem.Designation;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetConcept {
  private String code;
  private String codeSystem;
  private Designation display;
  private List<Designation> designations;
  private List<MapSetAssociation> associations;
}
