package com.kodality.termx.terminology.terminology.valueset.ruleset;

import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionRepository;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionRuleSetService {
  private final ValueSetVersionRuleSetRepository repository;
  private final ValueSetVersionRepository valueSetVersionRepository;
  private final ValueSetVersionRuleRepository valueSetVersionRuleRepository;

  public Optional<ValueSetVersionRuleSet> load(String valueSet, String valueSetVersion) {
    return Optional.ofNullable(repository.load(valueSet, valueSetVersion));
  }

  public Optional<ValueSetVersionRuleSet> load(Long valueSetVersionId) {
    return Optional.ofNullable(decorate(repository.load(valueSetVersionId)));
  }

  private ValueSetVersionRuleSet decorate(ValueSetVersionRuleSet ruleSet) {
    if (ruleSet != null) {
      ruleSet.setRules(valueSetVersionRuleRepository.loadAll(ruleSet.getId()));
    }
    return ruleSet;
  }

  @Transactional
  public ValueSetVersionRuleSet save(ValueSetVersionRuleSet ruleSet, String valueSet, String valueSetVersion) {
    Long versionId = valueSetVersionRepository.load(valueSet, valueSetVersion).getId();
    repository.save(ruleSet, versionId);
    return ruleSet;
  }
}
