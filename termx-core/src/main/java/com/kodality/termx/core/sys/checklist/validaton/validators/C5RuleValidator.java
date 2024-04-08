package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionErrorResource;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
public class C5RuleValidator implements CodeSystemRuleValidator {
  private final static int MAX_DESIGNATION_LENGTH = 500;
  @Override
  public String getRuleCode() {
    return "C5";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    return concepts.stream().filter(c -> whitelists.stream().noneMatch(wl -> "Concept".equals(wl.getResourceType()) && c.getCode().equals(wl.getResourceId())))
        .flatMap(c -> Optional.ofNullable(c.getVersions()).orElse(List.of()).stream())
        .flatMap(v -> v.getDesignations().stream().filter(d -> d.getName().length() > MAX_DESIGNATION_LENGTH).map(d -> Pair.of(v.getCode(), d.getName())))
        .map(p -> new ChecklistAssertionError()
            .setError(String.format("The concept's '%s' designation length exceeds the maximum allowed number of symbols (%d).", p.getKey(), MAX_DESIGNATION_LENGTH))
            .setResources(List.of(new ChecklistAssertionErrorResource().setResourceType("Concept").setResourceId(p.getKey())))
        ).toList();
  }
}
