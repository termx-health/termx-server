package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionErrorResource;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
public class C3RuleValidator implements CodeSystemRuleValidator {
  @Override
  public String getRuleCode() {
    return "C3";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    return concepts.stream().filter(c -> whitelists.stream().noneMatch(wl -> "Concept".equals(wl.getResourceType()) && c.getCode().equals(wl.getResourceId())))
        .flatMap(c -> Optional.ofNullable(c.getVersions()).orElse(List.of()).stream())
        .flatMap(v -> v.getDesignations().stream().filter(d -> PublicationStatus.active.equals(d.getStatus()) && d.isPreferred()).map(d -> Pair.of(v.getCode(), d)))
        .collect(Collectors.groupingBy(d -> d.getValue().getName() + d.getValue().getLanguage())).values().stream()
        .map(val -> {
          List<String> codes = val.stream().map(Pair::getKey).toList();
          return codes.size() > 1 ? new ChecklistAssertionError()
              .setError(String.format("Concepts '%s' have identical terms", String.join("','", codes)))
              .setResources(codes.stream().map(c -> new ChecklistAssertionErrorResource().setResourceType("Concept").setResourceId(c)).toList())
              : null;
        }).filter(Objects::nonNull).toList();
  }
}
