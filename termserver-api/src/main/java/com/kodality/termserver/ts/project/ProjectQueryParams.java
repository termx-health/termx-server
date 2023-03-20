package com.kodality.termserver.ts.project;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ProjectQueryParams extends QueryParams {
  private String textContains;
}
