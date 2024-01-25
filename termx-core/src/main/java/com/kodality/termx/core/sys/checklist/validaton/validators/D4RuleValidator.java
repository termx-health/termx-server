package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class D4RuleValidator implements CodeSystemRuleValidator {

  @Override
  public String getRuleCode() {
    return "D4";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    boolean replacedBy = Optional.ofNullable(codeSystem.getProperties()).orElse(List.of()).stream().anyMatch(p -> "replacedby".equals(p.getName()));
    if (!replacedBy) {
      return List.of(new ChecklistAssertionError().setError("The CodeSystem does not support ability to specify replacement concept. Property replacedby is not defined."));
    }
    return List.of();
  }
}
