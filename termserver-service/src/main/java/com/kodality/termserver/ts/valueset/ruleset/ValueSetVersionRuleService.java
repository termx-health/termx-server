package com.kodality.termserver.ts.valueset.ruleset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.valueset.ValueSetVersionRuleQueryParams;
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

  private final UserPermissionService userPermissionService;

  public Optional<ValueSetVersionRule> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public List<ValueSetVersionRule> loadAll(Long ruleSetId) {
    return repository.loadAll(ruleSetId);
  }

  @Transactional
  public void save(List<ValueSetVersionRule> rules, Long ruleSetId, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");

    repository.retain(rules, ruleSetId);
    if (rules != null) {
      rules.forEach(rule -> save(rule, ruleSetId, valueSet));
    }
  }

  @Transactional
  public void save(ValueSetVersionRule rule, Long ruleSetId, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");
    repository.save(rule, ruleSetId);
  }

  @Transactional
  public void delete(Long id, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");
    repository.delete(id);
  }

  public QueryResult<ValueSetVersionRule> query(ValueSetVersionRuleQueryParams params) {
    return repository.query(params);
  }

}
