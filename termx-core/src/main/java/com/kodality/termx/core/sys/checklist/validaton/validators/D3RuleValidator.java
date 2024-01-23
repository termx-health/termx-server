package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.EntityProperty;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class D3RuleValidator implements CodeSystemRuleValidator {
  @Override
  public String getRuleCode() {
    return "D3";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    List<EntityProperty> properties = Optional.ofNullable(codeSystem.getProperties()).orElse(List.of()).stream().filter(p -> "property".equals(p.getKind())).toList();
    if (CollectionUtils.isEmpty(properties)) {
      return List.of(new ChecklistAssertionError().setError("The properties are not defined within a CodeSystem"));
    }
    List<Concept> notDefinedConcepts = concepts.stream().filter(c -> c.getVersions().stream()
        .anyMatch(v -> CollectionUtils.isEmpty(v.getPropertyValues()) && CollectionUtils.isEmpty(v.getAssociations()))).toList();
    return notDefinedConcepts.stream()
        .map(c -> new ChecklistAssertionError().setError(String.format("The concept '%s' definition does not use properties or associations.", c.getCode())))
        .toList();
  }
}
