package org.termx.taskforge.workflow;

import com.kodality.commons.model.QueryResult;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.validation.Validated;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
public class WorkflowController {
  private final WorkflowService workflowService;

  @Get("{id}")
  public Workflow load(@PathVariable Long id) {
    return workflowService.load(id);
  }

  @Get("/{?params*}")
  public QueryResult<Workflow> search(WorkflowSearchParams params) {
    return workflowService.search(params);
  }

}
