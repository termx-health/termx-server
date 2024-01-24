package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionErrorResource;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class C2RuleValidator implements CodeSystemRuleValidator {

  @Override
  public String getRuleCode() {
    return "C2";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    return concepts.stream().filter(c -> whitelists.stream().noneMatch(wl -> "Concept".equals(wl.getResourceType()) && c.getCode().equals(wl.getResourceId())))
        .flatMap(c -> c.getVersions().stream())
        .flatMap(v -> {
          Map<String, List<Designation>> designations =
              Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream().collect(Collectors.groupingBy(Designation::getLanguage));
          return designations.entrySet().stream().map(group -> {
            long preferredDesignationCount = group.getValue().stream().filter(Designation::isPreferred).count();
            if (preferredDesignationCount > 1) {
              return new ChecklistAssertionError()
                  .setError(String.format("The concept '%s' has more than one preferred designation in '%s' language", v.getCode(), group.getKey()))
                  .setResources(List.of(new ChecklistAssertionErrorResource().setResourceType("Concept").setResourceId(v.getCode())));
            }
            if (preferredDesignationCount == 0) {
              return new ChecklistAssertionError()
                  .setError(String.format("The concept '%s' has no preferred designation in '%s' language", v.getCode(), group.getKey()))
                  .setResources(List.of(new ChecklistAssertionErrorResource().setResourceType("Concept").setResourceId(v.getCode())));
            }
            return null;
          });
        }).filter(Objects::nonNull).collect(Collectors.toList());
  }
}
