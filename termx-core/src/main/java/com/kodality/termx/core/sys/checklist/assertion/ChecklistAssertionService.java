package com.kodality.termx.core.sys.checklist.assertion;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.checklist.ChecklistService;
import com.kodality.termx.sys.checklist.Checklist;
import com.kodality.termx.sys.checklist.ChecklistAssertion;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.sys.checklist.ChecklistAssertionQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.time.OffsetDateTime;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ChecklistAssertionService {
  private final ChecklistService checklistService;
  private final ChecklistAssertionRepository repository;

  public QueryResult<ChecklistAssertion> query(ChecklistAssertionQueryParams params) {
    return repository.query(params);
  }

  public ChecklistAssertion create(Long checklistId, String resourceVersion, boolean passed) {
    ChecklistAssertion assertion = new ChecklistAssertion();
    assertion.setResourceVersion(resourceVersion);
    assertion.setPassed(passed);
    return create(checklistId, assertion);
  }

  public ChecklistAssertion create(Long checklistId, String resourceVersion, List<ChecklistAssertionError> errors) {
    ChecklistAssertion assertion = new ChecklistAssertion();
    assertion.setResourceVersion(resourceVersion);
    assertion.setPassed(CollectionUtils.isEmpty(errors));
    assertion.setErrors(errors);
    return create(checklistId, assertion);
  }

  private ChecklistAssertion create(Long checklistId, ChecklistAssertion assertion) {
    Checklist checklist = checklistService.load(checklistId);

    assertion.setExecutor(SessionStore.require().getUsername());
    assertion.setExecutionDate(OffsetDateTime.now());
    assertion.setChecklistId(checklist.getId());
    assertion.setRuleId(checklist.getRule().getId());

    repository.save(assertion);
    return assertion;
  }
}
