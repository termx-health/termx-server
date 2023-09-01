package com.kodality.termx.ts.mapset;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetAssociationQueryParams extends QueryParams {
  private Long id;
  private String mapSet;
  private List<String> permittedMapSets;
  private String mapSetVersion;
  private Long mapSetVersionId;
  private String relationships;
  private String sourceCodes;
  private String sourceCodeAndSystem; //code|system
  private String targetCodeAndSystem; //code|system
  private String sourceCodeAndSystemUri; //code|systemUri
  private String targetCodeAndSystemUri; //code|systemUri
  private Boolean noMap;
  private Boolean verified;
}
