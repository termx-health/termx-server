package com.kodality.termx.sys.space;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SpaceQueryParams extends QueryParams {
  private String ids;
  private String codes;
  private String textContains;
  private String resource;

  private List<Long> permittedIds;
}
