package org.termx.taskforge.workflow;

import com.kodality.commons.model.QueryResult;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class WorkflowService {
  private final WorkflowRepository workflowRepository;

  public void save(Long projectId, List<Workflow> workflows) {
    if (CollectionUtils.isEmpty(workflows)) {
      return;
    }
    workflowRepository.save(projectId, workflows);
  }

  public Workflow load(Long id) {
    return workflowRepository.load(id);
  }

  public QueryResult<Workflow> search(WorkflowSearchParams params) {
    return workflowRepository.search(params);
  }

}
