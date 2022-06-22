package com.kodality.termserver.auth;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PrivilegeQueryParams extends QueryParams {
  private String code;
  private String codeContains;
}
