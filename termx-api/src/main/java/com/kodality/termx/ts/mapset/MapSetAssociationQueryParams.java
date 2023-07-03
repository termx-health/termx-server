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
  private String status;
  private String type;

  private String sourceCode;
  private String sourceSystem;
  private String sourceSystemUri;
  private String sourceSystemVersion;

  private String targetCode;
  private String targetSystem;
  private String targetSystemVersion;

  private String mapSet;
  private List<String> permittedMapSets;
  private String mapSetVersion;
  private Long mapSetVersionId;
}
