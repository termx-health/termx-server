package org.termx.core.sys.checklist.validaton.validators;

import org.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import org.termx.sys.checklist.Checklist.ChecklistWhitelist;
import org.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.Concept;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class D2RuleValidator implements CodeSystemRuleValidator {

  @Override
  public String getRuleCode() {
    return "D2";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    return codeSystem.getSequence() != null ? null :
        List.of(new ChecklistAssertionError().setError("The sequence for the generation of unique numbers is not created for the CodeSystem."));
  }
}
