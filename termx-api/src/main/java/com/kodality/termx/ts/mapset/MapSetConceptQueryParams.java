package com.kodality.termx.ts.mapset;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetConceptQueryParams extends QueryParams {
  private String type; // source or target
  private String textContains;
  private Boolean verified;
  private Boolean unmapped;
}
