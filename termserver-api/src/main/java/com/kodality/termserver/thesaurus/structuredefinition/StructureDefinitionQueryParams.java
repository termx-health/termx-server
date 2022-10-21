package com.kodality.termserver.thesaurus.structuredefinition;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class StructureDefinitionQueryParams extends QueryParams {
  private String code;
  private String textContains;
}
