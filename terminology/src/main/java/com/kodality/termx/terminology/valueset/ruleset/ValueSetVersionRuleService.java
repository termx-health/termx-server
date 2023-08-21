package com.kodality.termx.terminology.valueset.ruleset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ApiError;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionRuleService {
  private final ValueSetVersionRuleRepository repository;
  private final ValueSetVersionRuleSetRepository ruleSetRepository;

  private final UserPermissionService userPermissionService;

  public Optional<ValueSetVersionRule> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public List<ValueSetVersionRule> loadAll(Long ruleSetId) {
    return repository.loadAll(ruleSetId);
  }

  @Transactional
  public void save(List<ValueSetVersionRule> rules, String valueSet, String valueSetVersion) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");

    Long ruleSetId = ruleSetRepository.load(valueSet, valueSetVersion).getId();

    repository.retain(rules, ruleSetId);
    if (rules != null) {
      rules.forEach(rule -> repository.save(rule, ruleSetId));
    }
  }

  @Transactional
  public void save(ValueSetVersionRule rule, String valueSet, String valueSetVersion) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");
    validate(rule);
    repository.save(rule, ruleSetRepository.load(valueSet, valueSetVersion).getId());
  }

  @Transactional
  public void delete(Long id, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");
    repository.delete(id);
  }

  public QueryResult<ValueSetVersionRule> query(ValueSetVersionRuleQueryParams params) {
    return repository.query(params);
  }

  private void validate(ValueSetVersionRule rule) {
    if (rule.getConcepts() != null) {
      boolean notDefined = rule.getConcepts().stream().anyMatch(c -> c.getConcept() == null || c.getConcept().getCode() == null);
      if (notDefined) {
        throw ApiError.TE308.toApiException();
      }

      Map<String, List<ValueSetVersionConcept>> grouped = rule.getConcepts().stream().collect(Collectors.groupingBy(c -> c.getConcept().getCode() + (c.getConcept().getCodeSystem() != null ? "|" + c.getConcept().getCodeSystem() : "")));
      if (rule.getConcepts().size() != grouped.keySet().size()) {
        throw ApiError.TE309.toApiException(Map.of("duplicates", grouped.entrySet().stream().filter(es -> es.getValue().size() > 1).map(Entry::getKey).collect(Collectors.joining(", "))));
      }
    }
  }
}
