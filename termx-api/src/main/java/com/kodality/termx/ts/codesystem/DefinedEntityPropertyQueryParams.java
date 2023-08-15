package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class DefinedEntityPropertyQueryParams extends QueryParams {
  private String textContains;
}
