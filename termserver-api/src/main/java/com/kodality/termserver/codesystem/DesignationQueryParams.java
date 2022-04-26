package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class DesignationQueryParams extends QueryParams {
  private String valueSet;
  private String valueSetVersion;
}
