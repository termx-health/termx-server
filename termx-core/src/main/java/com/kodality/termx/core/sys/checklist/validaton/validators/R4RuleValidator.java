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
public class R4RuleValidator implements CodeSystemRuleValidator {

  @Override
  public String getRuleCode() {
    return "R4";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    return concepts.stream().flatMap(c -> c.getVersions().stream())
        .filter(v -> Optional.ofNullable(v.getAssociations()).orElse(List.of()).stream().anyMatch(a -> !a.getStatus().equals(v.getStatus())))
        .map(v -> new ChecklistAssertionError().setError(String.format("The concept '%s' has associations with status not according to concept version status.", v.getCode())))
        .toList();
  }
}
