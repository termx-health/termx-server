package com.kodality.termx.taskflow;

import com.kodality.taskflow.api.TaskStatusChangeInterceptor;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskStatus;
import com.kodality.taskflow.workflow.Workflow;
import com.kodality.taskflow.workflow.WorkflowService;
import com.kodality.termx.task.task.TaskType;
import com.kodality.termx.terminology.terminology.mapset.MapSetProvenanceService;
import com.kodality.termx.terminology.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.mapset.MapSetVersion;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class MapSetTaskStatusChangeInterceptor extends TaskStatusChangeInterceptor {
  private final WorkflowService workflowService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetProvenanceService provenanceService;

  public static final String MS_VERSION = "map-set-version";


  @Override
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getWorkflowId() == null || task.getContext() == null || !TaskStatus.accepted.equals(task.getStatus())) {
      return;
    }
    Workflow workflow = workflowService.load(task.getWorkflowId());
    Optional<Long> msVersionId = task.getContext().stream().filter(ctx -> MS_VERSION.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId());

    if (workflow.getTaskType().equals(TaskType.version_review) && msVersionId.isPresent()) {
      MapSetVersion msv = mapSetVersionService.load(msVersionId.get());
      provenanceService.provenanceMapSetVersion("reviewed", msv.getMapSet(), msv.getVersion(), () -> {});
    }
    if (workflow.getTaskType().equals(TaskType.version_approval) && msVersionId.isPresent()) {
      MapSetVersion msv = mapSetVersionService.load(msVersionId.get());
      provenanceService.provenanceMapSetVersion("approved", msv.getMapSet(), msv.getVersion(),
          () -> mapSetVersionService.activate(msv.getMapSet(), msv.getVersion()));
    }
  }
}
