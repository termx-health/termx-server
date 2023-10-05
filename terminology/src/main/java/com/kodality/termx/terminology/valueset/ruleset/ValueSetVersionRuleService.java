package com.kodality.termx.terminology.valueset.ruleset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ApiError;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
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
  private final ValueSetVersionRuleSetService ruleSetService;

  public Optional<ValueSetVersionRule> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public List<ValueSetVersionRule> loadAll(Long ruleSetId) {
    return repository.loadAll(ruleSetId);
  }

  @Transactional
  public void save(List<ValueSetVersionRule> rules, String valueSet, String valueSetVersion) {
    ValueSetVersionRuleSet ruleSet = ruleSetService.load(valueSet, valueSetVersion).orElse(null);
    if (ruleSet == null) {
      ruleSet = ruleSetService.save(new ValueSetVersionRuleSet(), valueSet, valueSetVersion);
    }

    repository.retain(rules, ruleSet.getId());
    if (rules != null) {
      Long rulSetId = ruleSet.getId();
      rules.forEach(rule -> repository.save(rule, rulSetId));
    }
  }

  @Transactional
  public void save(ValueSetVersionRule rule, String valueSet, String valueSetVersion) {
    validate(rule);

    Long ruleSetId = ruleSetService.load(valueSet, valueSetVersion).map(ValueSetVersionRuleSet::getId).orElse(null);
    if (ruleSetId == null) {
      ruleSetId = ruleSetService.save(new ValueSetVersionRuleSet(), valueSet, valueSetVersion).getId();
    }

    repository.save(rule, ruleSetId);
  }

  @Transactional
  public void delete(Long id) {
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
