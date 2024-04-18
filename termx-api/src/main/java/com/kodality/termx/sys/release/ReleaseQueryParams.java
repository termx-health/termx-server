package com.kodality.termx.sys.release;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ReleaseQueryParams extends QueryParams {
  private String textContains;
  private String status;
  private String resource; //type or type|id or type|id|version

  private List<Long> permittedIds;
}
