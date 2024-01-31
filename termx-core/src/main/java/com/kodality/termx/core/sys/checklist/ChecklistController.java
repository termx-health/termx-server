package com.kodality.termx.core.sys.checklist;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.checklist.assertion.ChecklistAssertionService;
import com.kodality.termx.core.sys.checklist.validaton.ChecklistValidationService;
import com.kodality.termx.core.sys.checklist.rule.ChecklistRuleService;
import com.kodality.termx.sys.checklist.Checklist;
import com.kodality.termx.sys.checklist.ChecklistAssertion;
import com.kodality.termx.sys.checklist.ChecklistQueryParams;
import com.kodality.termx.sys.checklist.ChecklistRule;
import com.kodality.termx.sys.checklist.ChecklistRuleQueryParams;
import com.kodality.termx.sys.checklist.ChecklistValidationRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/checklists")
@RequiredArgsConstructor
public class ChecklistController {
  private final ChecklistService service;
  private final ChecklistRuleService ruleService;
  private final ChecklistAssertionService assertionService;
  private final ChecklistValidationService validationService;

  //----------------Checklist----------------

  @Authorized(Privilege.C_VIEW)
  @Get("{?params*}")
  public QueryResult<Checklist> search(ChecklistQueryParams params) {
    return service.query(params);
  }

  @Authorized(privilege = Privilege.C_EDIT)
  @Post()
  public HttpResponse<?> save(@Valid @Body ChecklistRequest request) {
    service.save(request.getChecklist(), request.getResourceType(), request.getResourceId());
    return HttpResponse.ok();
  }


  //----------------Rule----------------

  @Authorized(privilege = Privilege.C_EDIT)
  @Post("/rules")
  public ChecklistRule create(@Valid @Body ChecklistRule rule) {
    rule.setId(null);
    ruleService.save(rule);
    return rule;
  }

  @Authorized(Privilege.C_EDIT)
  @Put("/rules/{id}")
  public ChecklistRule update(@PathVariable Long id, @Valid @Body ChecklistRule rule) {
    rule.setId(id);
    ruleService.save(rule);
    return ruleService.save(rule);
  }

  @Authorized(Privilege.C_VIEW)
  @Get("/rules/{id}")
  public ChecklistRule load(@PathVariable Long id) {
    return ruleService.load(id);
  }

  @Authorized(Privilege.C_VIEW)
  @Get("/rules{?params*}")
  public QueryResult<ChecklistRule> search(ChecklistRuleQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.C_VIEW, Long::valueOf));
    return ruleService.query(params);
  }

  @Authorized(Privilege.C_EDIT)
  @Delete(uri = "/rules/{id}")
  public HttpResponse<?> deleteRule(@PathVariable Long id) {
    ruleService.delete(id);
    return HttpResponse.ok();
  }

  //----------------Rule----------------

  @Authorized(privilege = Privilege.C_EDIT)
  @Post("/{id}/assertions")
  public ChecklistAssertion create(@PathVariable Long id, @Valid @Body Map<String, Boolean> body) {
    return assertionService.create(id, body.getOrDefault("passed", false));
  }

  @Authorized(privilege = Privilege.C_EDIT)
  @Post("/assertions/run-checks")
  public HttpResponse<?> runChecks(@Valid @Body ChecklistValidationRequest request) {
    validationService.runChecks(request);
    return HttpResponse.ok();
  }

  @Getter
  @Setter
  public static class ChecklistRequest {
    private List<Checklist> checklist;
    private String resourceId;
    private String resourceType;
  }
}
