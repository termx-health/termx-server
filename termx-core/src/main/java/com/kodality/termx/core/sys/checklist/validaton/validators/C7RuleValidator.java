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
import java.util.regex.Pattern;

@Singleton
public class C7RuleValidator implements CodeSystemRuleValidator {
  private final Pattern STRING_REGEX = Pattern.compile("^(?!.*\\s\\s)(?!.*\\s$)(?!^\\s)[^\\t\\n@$#\\\\]+$");

  @Override
  public String getRuleCode() {
    return "C7";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    return concepts.stream().filter(c -> whitelists.stream().noneMatch(wl -> "Concept".equals(wl.getResourceType()) && c.getCode().equals(wl.getResourceId())))
        .filter(c -> !STRING_REGEX.matcher(c.getCode()).matches() ||
            Optional.ofNullable(c.getVersions()).orElse(List.of()).stream().anyMatch(v -> v.getDesignations().stream().anyMatch(d -> !STRING_REGEX.matcher(d.getName()).matches())))
        .map(c -> new ChecklistAssertionError()
            .setError(String.format("The concept '%s' code or designation contains: tabs, newlines, leading/trailing whitespaces, double spaces or characters @, $, #, \\.", c.getCode()))
            .setResources(List.of(new ChecklistAssertionErrorResource().setResourceType("Concept").setResourceId(c.getCode())))
        ).toList();
  }
}
