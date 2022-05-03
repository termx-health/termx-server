package com.kodality.termserver.namingsystem;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NamingSystemQueryParams extends QueryParams {
  private String name;
  private String codeSystem;
}
