package com.kodality.termx.core.sys.checklist.validaton;

import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.sys.checklist.ChecklistService;
import com.kodality.termx.core.sys.checklist.assertion.ChecklistAssertionService;
import com.kodality.termx.core.ts.CodeSystemProvider;
import com.kodality.termx.sys.checklist.Checklist;
import com.kodality.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import com.kodality.termx.sys.checklist.ChecklistQueryParams;
import com.kodality.termx.sys.checklist.ChecklistValidationRequest;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ChecklistValidationService {
  private final ChecklistService checklistService;
  private final ChecklistAssertionService checklistAssertionService;
  private final List<CodeSystemRuleValidator> codeSystemValidators;
  private final CodeSystemProvider codeSystemProvider;

  private static final String RULE_VERIFICATION = "software";

  @Transactional
  public void runChecks(ChecklistValidationRequest request) {
    if (request.getResourceVersion() == null) {
      throw ApiError.TC116.toApiException();
    }
    if ((request.getResourceId() == null || request.getResourceType() == null) && request.getChecklistId() == null) {
      throw ApiError.TC113.toApiException();
    }
    ChecklistQueryParams params = new ChecklistQueryParams().all();
    params.setResourceType(request.getResourceType());
    params.setResourceId(request.getResourceId());
    params.setIds(request.getChecklistId() == null ? null : String.valueOf(request.getChecklistId()));
    params.setRuleTarget(request.getRuleTarget());

    List<Checklist> checklists = checklistService.query(params).getData();

    checklists.stream().collect(Collectors.groupingBy(checklist -> PipeUtil.toPipe(checklist.getResourceType(), checklist.getResourceId()))).entrySet().forEach(group -> {
      String[] resource = PipeUtil.parsePipe(group.getKey());
      if (resource[0].equals("CodeSystem")) {
        runCodeSystemChecks(resource[1], group.getValue(), request.getResourceVersion());
      }
    });
  }

  private void runCodeSystemChecks(String codeSystemId, List<Checklist> checklists, String version) {
    CodeSystem codeSystem = codeSystemProvider.loadCodeSystem(codeSystemId);
    List<Concept> concepts = codeSystemProvider.searchConcepts(new ConceptQueryParams().setCodeSystem(codeSystemId).all()).getData();

    checklists.forEach(checklist -> {
      Optional<CodeSystemRuleValidator> validator = codeSystemValidators.stream().filter(v -> v.getRuleCode().equals(checklist.getRule().getCode())).findFirst();
      if (validator.isEmpty() && RULE_VERIFICATION.equals(checklist.getRule().getVerification())) {
        List<ChecklistAssertionError> errors = List.of(new ChecklistAssertionError().setError(String.format("Validator %s is not implemented", checklist.getRule().getCode())));
        checklistAssertionService.create(checklist.getId(), version, errors);
      } else validator.ifPresent(codeSystemRuleValidator ->
          checklistAssertionService.create(checklist.getId(), version, codeSystemRuleValidator.validate(codeSystem, concepts, Optional.ofNullable(checklist.getWhitelist()).orElse(List.of()))));
    });
  }
}
