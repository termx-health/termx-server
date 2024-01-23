package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
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
