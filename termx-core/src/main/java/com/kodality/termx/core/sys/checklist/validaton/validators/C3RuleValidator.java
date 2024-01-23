package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    return concepts.stream().flatMap(c -> c.getVersions().stream())
        .flatMap(v -> v.getDesignations().stream().filter(d -> PublicationStatus.active.equals(d.getStatus()) && d.isPreferred()).map(d -> Pair.of(v.getCode(), d)))
        .collect(Collectors.groupingBy(d -> d.getValue().getName() + d.getValue().getLanguage())).values().stream()
        .map(val -> {
          Set<String> codes = val.stream().collect(Collectors.groupingBy(Pair::getKey)).keySet();
          return codes.size() > 1 ? new ChecklistAssertionError().setError(String.format("Concepts '%s' have identical terms", String.join("','", codes))) : null;
        }).filter(Objects::nonNull).toList();
  }
}
