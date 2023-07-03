package com.kodality.termx.ts.mapset;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetEntityVersionQueryParams extends QueryParams {
  private Long mapSetEntityId;
  private String mapSetEntityIds;
  private Long mapSetVersionId;
  private String mapSetVersion;
  private String mapSet;
  private List<String> permittedMapSets;
  private String status;
  private String descriptionContains;
}

