package com.kodality.termserver.mapset;

import com.kodality.termserver.commons.model.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetAssociationQueryParams extends QueryParams {
  private Long id;
  private String mapSet;
  private String associationType;
  private String mapSetVersion;
  private String status;
}
