package com.kodality.termserver.sys.space;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SpaceQueryParams extends QueryParams {
  private String textContains;
}
