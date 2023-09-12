package com.kodality.termx.ts.mapset;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetPropertyQueryParams extends QueryParams {
  private String ids;
  private String names;
  private String mapSet;
  private List<String> permittedMapSets;
}
