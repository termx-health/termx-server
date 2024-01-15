package com.kodality.termx.core.sys.checklist;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.checklist.ChecklistRule;
import com.kodality.termx.sys.checklist.ChecklistRuleQueryParams;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/checklist-rules")
@RequiredArgsConstructor
public class ChecklistController {
  private final ChecklistService service;


  //----------------Rule----------------

  @Authorized(privilege = Privilege.C_EDIT)
  @Post()
  public ChecklistRule create(@Valid @Body ChecklistRule rule) {
    rule.setId(null);
    service.save(rule);
    return rule;
  }

  @Authorized(Privilege.C_EDIT)
  @Put("/{id}")
  public ChecklistRule update(@PathVariable Long id, @Valid @Body ChecklistRule rule) {
    rule.setId(id);
    service.save(rule);
    return service.save(rule);
  }

  @Authorized(Privilege.C_VIEW)
  @Get("{id}")
  public ChecklistRule load(@PathVariable Long id) {
    return service.load(id);
  }

  @Authorized(Privilege.C_VIEW)
  @Get("/{?params*}")
  public QueryResult<ChecklistRule> search(ChecklistRuleQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.C_VIEW, Long::valueOf));
    return service.query(params);
  }
}
