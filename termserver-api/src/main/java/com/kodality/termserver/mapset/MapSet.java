package com.kodality.termserver.mapset;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MapSet {
  private Long id;
  private String name;
  private String description;

  private MapSetVersion version;
}
