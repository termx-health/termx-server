package com.kodality.termx.bob;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class BobObjectQueryParams extends QueryParams {
  private String meta;
}
