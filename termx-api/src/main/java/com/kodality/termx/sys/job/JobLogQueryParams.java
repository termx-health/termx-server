package com.kodality.termx.sys.job;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobLogQueryParams extends QueryParams {
  private Long id;
  private String type;
  private String status;
}
