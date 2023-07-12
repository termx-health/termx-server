package com.kodality.termx.taskflow.valueset;

import com.kodality.taskflow.api.TaskStatusChangeInterceptor;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskStatus;
import com.kodality.taskflow.workflow.Workflow;
import com.kodality.taskflow.workflow.WorkflowService;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.task.TaskType;
import com.kodality.termx.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetTaskStatusChangeInterceptor extends TaskStatusChangeInterceptor {
  private final WorkflowService workflowService;
  private final ProvenanceService provenanceService;
  private final ValueSetVersionService valueSetVersionService;
  public static final String VS_VERSION = "value-set-version";

  @Override
  @Transactional
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getWorkflowId() == null || task.getContext() == null || !TaskStatus.accepted.equals(task.getStatus())) {
      return;
    }
    Workflow workflow = workflowService.load(task.getWorkflowId());
    Optional<Long> vsVersionId = task.getContext().stream().filter(ctx -> VS_VERSION.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId());

    if (workflow.getTaskType().equals(TaskType.version_review) && vsVersionId.isPresent()) {
      createValueSetVersionProvenance("reviewed", vsVersionId.get());
    }
    if (workflow.getTaskType().equals(TaskType.version_approval) && vsVersionId.isPresent()) {
      createValueSetVersionProvenance("approved", vsVersionId.get());
    }
  }

  private void createValueSetVersionProvenance(String activity, Long vsVersionId) {
    ValueSetVersion version = valueSetVersionService.load(vsVersionId);
    provenanceService.create(new Provenance(activity, "ValueSetVersion", version.getId().toString())
        .addContext("part-of", "ValueSet", version.getValueSet()));
    if (activity.equals("approved")) {
      valueSetVersionService.activate(version.getValueSet(), version.getVersion());
    }
  }
}
