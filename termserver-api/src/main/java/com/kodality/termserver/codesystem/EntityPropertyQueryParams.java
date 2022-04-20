package com.kodality.termserver.codesystem;

import com.kodality.termserver.commons.model.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EntityPropertyQueryParams extends QueryParams {
  private String names;
  private String codeSystem;
}
