package com.kodality.termserver.job;

import com.kodality.termserver.commons.model.model.QueryParams;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobLogQueryParams extends QueryParams {
  private Long id;
  private String type;
  private String status;
}
