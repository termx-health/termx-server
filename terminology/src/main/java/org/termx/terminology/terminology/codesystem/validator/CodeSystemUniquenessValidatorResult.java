
package org.termx.terminology.terminology.codesystem.validator;

import org.termx.ts.codesystem.Concept;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodeSystemUniquenessValidatorResult {
  private Map<String, List<Concept>> duplicates;
}
