package com.kodality.termserver.ts.valueset.ruleset;

import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionRuleService {
  private final ValueSetVersionRuleRepository repository;

  public Optional<ValueSetVersionRule> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public List<ValueSetVersionRule> loadAll(Long ruleSetId) {
    return repository.loadAll(ruleSetId);
  }

  @Transactional
  public void save(List<ValueSetVersionRule> rules, Long ruleSetId) {
    repository.retain(rules, ruleSetId);
    if (rules != null) {
      rules.forEach(rule -> save(rule, ruleSetId));
    }
  }

  @Transactional
  public void save(ValueSetVersionRule rule, Long ruleSetId) {
    repository.save(rule, ruleSetId);
  }

  @Transactional
  public void delete(Long id) {
    repository.delete(id);
  }

}
