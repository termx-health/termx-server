package com.kodality.termserver.mapset;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class MapSetEntity {
  private Long id;

  private MapSetEntityVersion version;
}
