package com.kodality.termserver.terminology.valueset.ruleset;

import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionRepository;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionRuleSetService {
  private final ValueSetVersionRuleSetRepository repository;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final ValueSetVersionRepository valueSetVersionRepository;

  private final UserPermissionService userPermissionService;

  public Optional<ValueSetVersionRuleSet> load(String valueSet) {
    return load(valueSetVersionRepository.loadLastVersion(valueSet).getId());
  }

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
}
