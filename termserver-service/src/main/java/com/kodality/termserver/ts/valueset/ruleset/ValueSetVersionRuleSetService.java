package com.kodality.termserver.ts.valueset.ruleset;

import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionRuleSetService {
  private final ValueSetVersionRuleSetRepository repository;
  private final ValueSetVersionRuleService valueSetVersionRuleService;

  private final UserPermissionService userPermissionService;

  public Optional<ValueSetVersionRuleSet> load(Long valueSetVersionId) {
    return Optional.ofNullable(decorate(repository.load(valueSetVersionId)));
  }

  private ValueSetVersionRuleSet decorate(ValueSetVersionRuleSet ruleSet) {
    if (ruleSet != null) {
      ruleSet.setRules(valueSetVersionRuleService.loadAll(ruleSet.getId()));
    }
    return ruleSet;
  }

  @Transactional
  public void save(ValueSetVersionRuleSet ruleSet, Long valueSetVersionId, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");
    repository.save(ruleSet, valueSetVersionId);
  }

  @Transactional
  public void cancel(Long valueSetVersionId, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");

    Optional<ValueSetVersionRuleSet> ruleSet = load(valueSetVersionId);
    if (ruleSet.isPresent()) {
      valueSetVersionRuleService.save(List.of(), ruleSet.get().getId(), valueSet);
      repository.cancel(ruleSet.get().getId());
    }
  }
}
