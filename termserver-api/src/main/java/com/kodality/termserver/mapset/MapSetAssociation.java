package com.kodality.termserver.mapset;

import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MapSetAssociation extends MapSetEntity {
  private CodeSystemEntityVersion source;
  private CodeSystemEntityVersion target;
  private String associationType;
  private String status;
}
