package com.kodality.termx.modeler.structuredefinition;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class StructureDefinitionQueryParams extends QueryParams {
  private String ids;
  private String code;
  private String textContains;
  private String urls;
  private String contentFormat;
  private String version;

  private List<Long> permittedIds;

  private Long spaceId;
  private Long packageId;
  private Long packageVersionId;
}
