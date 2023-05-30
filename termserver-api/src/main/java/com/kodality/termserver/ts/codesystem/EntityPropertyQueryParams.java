package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EntityPropertyQueryParams extends QueryParams {
  private String ids;
  private String names;
  private String codeSystem;
  private List<String> permittedCodeSystems;
}
