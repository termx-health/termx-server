package com.kodality.termx.core.sys.checklist.rule;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.sys.checklist.ChecklistRule;
import com.kodality.termx.sys.checklist.ChecklistRuleQueryParams;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ChecklistRuleService {
  private final ChecklistRuleRepository repository;

  @Transactional
  public ChecklistRule save(ChecklistRule rule) {
    validate(rule);
    repository.save(rule);
    return rule;
  }

  private void validate(ChecklistRule rule) {
    ChecklistRule currentRule = repository.load(rule.getCode());
    if (currentRule != null && !currentRule.getId().equals(rule.getId())) {
      throw ApiError.TC112.toApiException(Map.of("code", rule.getCode()));
    }
  }

  public ChecklistRule load(Long id) {
    return repository.load(id);
  }

  public QueryResult<ChecklistRule> query(ChecklistRuleQueryParams params) {
    return repository.query(params);
  }

}
