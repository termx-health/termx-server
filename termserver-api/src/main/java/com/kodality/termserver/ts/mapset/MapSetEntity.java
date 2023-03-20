package com.kodality.termserver.ts.mapset;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class MapSetEntity {
  private Long id;
  private String mapSet;

  private List<MapSetEntityVersion> versions;
}
