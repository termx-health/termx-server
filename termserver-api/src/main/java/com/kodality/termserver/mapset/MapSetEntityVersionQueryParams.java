package com.kodality.termserver.mapset;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetEntityVersionQueryParams extends QueryParams {
  private Long mapSetEntityId;
  private Long mapSetVersionId;
  private String mapSetVersion;
  private String mapSet;
  private String status;
  private String descriptionContains;
}

