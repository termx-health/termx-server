package com.kodality.termx.sys.space;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SpaceQueryParams extends QueryParams {
  private String codes;
  private String textContains;
}
