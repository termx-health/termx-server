
package com.kodality.termx.terminology.codesystem.validator;

import com.kodality.termx.ts.codesystem.Concept;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodeSystemUniquenessValidatorResult {
  private Map<String, List<Concept>> duplicates;
}
