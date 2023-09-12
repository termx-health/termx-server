package com.kodality.termx.ts.property;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class DefinedPropertyQueryParams extends QueryParams {
  private String textContains;
}
