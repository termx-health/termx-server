package com.kodality.termx.ts.codesystem;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemConceptSupplementRequest {
  private List<Long> ids;
  private String snomedCode;
}
