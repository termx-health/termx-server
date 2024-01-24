package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionErrorResource;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class C1RuleValidator implements CodeSystemRuleValidator {

  @Override
  public String getRuleCode() {
    return "C1";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    return concepts.stream().filter(c -> whitelists.stream().noneMatch(wl -> "Concept".equals(wl.getResourceType()) && c.getCode().equals(wl.getResourceId())))
        .flatMap(c -> c.getVersions().stream())
        .filter(v -> CollectionUtils.isEmpty(v.getDesignations()))
        .map(v -> new ChecklistAssertionError()
            .setError(String.format("The concept '%s' does not have at least one designation", v.getCode()))
            .setResources(List.of(new ChecklistAssertionErrorResource().setResourceType("Concept").setResourceId(v.getCode())))
        ).toList();
  }
}
