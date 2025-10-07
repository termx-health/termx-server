package com.kodality.termx.ts.mapset;

import com.kodality.termx.ts.VersionReference;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetVersionReference extends VersionReference<MapSetVersionReference> {
  private String status;
}
