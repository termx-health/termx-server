package com.kodality.termx.core.sys.checklist.validaton.validators;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.sys.checklist.validaton.CodeSystemRuleValidator;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionErrorResource;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import jakarta.inject.Singleton;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Singleton
public class R1RuleValidator implements CodeSystemRuleValidator {

  @Override
  public String getRuleCode() {
    return "R1";
  }

  @Override
  public List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists) {
    return concepts.stream().filter(c -> whitelists.stream().noneMatch(wl -> "Concept".equals(wl.getResourceType()) && c.getCode().equals(wl.getResourceId())))
        .flatMap(c -> Optional.ofNullable(c.getVersions()).orElse(List.of()).stream())
        .collect(Collectors.groupingBy(v -> getPropKey(v.getPropertyValues()), mapping(CodeSystemEntityVersion::getCode, toList())))
        .values().stream().map(HashSet::new).filter(s -> s.size() > 1)
        .map(s -> new ChecklistAssertionError()
            .setError(String.format("Concepts '%s' have duplicate properties and property values.", String.join("','", s)))
            .setResources(s.stream().map(c -> new ChecklistAssertionErrorResource().setResourceType("Concept").setResourceId(c)).toList())
        ).toList();
  }

  private String getPropKey(List<EntityPropertyValue> propertyValues) {
    return Optional.ofNullable(propertyValues).orElse(List.of()).stream()
        .sorted(Comparator.comparing(EntityPropertyValue::getEntityPropertyId))
        .map(pv -> String.join("|", pv.getEntityProperty(), JsonUtil.toJson(pv.getValue())))
        .collect(Collectors.joining(","));
  }

}
