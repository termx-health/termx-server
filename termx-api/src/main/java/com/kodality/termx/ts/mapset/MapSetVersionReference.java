package com.kodality.termx.ts.mapset;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetVersionReference {
  private Long id;
  private String version;
  private String status;
}
