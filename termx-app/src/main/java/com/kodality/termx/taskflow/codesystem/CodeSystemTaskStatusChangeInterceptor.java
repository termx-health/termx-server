package com.kodality.termx.taskflow.codesystem;

import com.kodality.taskflow.api.TaskStatusChangeInterceptor;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskStatus;
import com.kodality.taskflow.workflow.Workflow;
import com.kodality.taskflow.workflow.WorkflowService;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.task.TaskType;
import com.kodality.termx.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemTaskStatusChangeInterceptor extends TaskStatusChangeInterceptor {
  private final WorkflowService workflowService;
  private final ProvenanceService provenanceService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  public static final String CS_VERSION = "code-system-version";
  public static final String CS_ENTITY_VERSION = "code-system-entity-version";

  @Override
  @Transactional
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getWorkflowId() == null || task.getContext() == null || !TaskStatus.accepted.equals(task.getStatus())) {
      return;
    }
    Workflow workflow = workflowService.load(task.getWorkflowId());
    Optional<Long> csVersionId = task.getContext().stream().filter(ctx -> CS_VERSION.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId());
    Optional<Long> csEntityVersionId = task.getContext().stream().filter(ctx -> CS_ENTITY_VERSION.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId());

    if (workflow.getTaskType().equals(TaskType.version_review) && csVersionId.isPresent()) {
      createCodeSystemVersionProvenance("reviewed", csVersionId.get());
    }
    if (workflow.getTaskType().equals(TaskType.version_approval) && csVersionId.isPresent()) {
      createCodeSystemVersionProvenance("approved", csVersionId.get());
    }
    if (workflow.getTaskType().equals(TaskType.concept_approval) && csEntityVersionId.isPresent()) {
      createConceptProvenance(csVersionId.orElse(null), csEntityVersionId.get());
    }
  }

  private void createCodeSystemVersionProvenance(String activity, Long csVersionId) {
    CodeSystemVersion version = codeSystemVersionService.load(csVersionId);
    provenanceService.create(new Provenance(activity, "CodeSystemVersion", version.getId().toString())
        .addContext("part-of", "CodeSystem", version.getCodeSystem()));
    if (activity.equals("approved")) {
      codeSystemVersionService.activate(version.getCodeSystem(), version.getVersion());
    }
  }

  private void createConceptProvenance(Long csVersionId, Long csEntityVersionId) {
    CodeSystemEntityVersion entityVersion = codeSystemEntityVersionService.load(csEntityVersionId);
    codeSystemEntityVersionService.activate(entityVersion.getId());

    Provenance provenance = new Provenance("approved", "CodeSystemEntityVersion", entityVersion.getId().toString())
        .addContext("part-of", "CodeSystem", entityVersion.getCodeSystem());
    if (csVersionId != null) {
      CodeSystemVersion version = codeSystemVersionService.load(csVersionId);
      provenance.addContext("part-of", "CodeSystemVersion", version.getId().toString());
      codeSystemVersionService.linkEntityVersions(version.getId(), List.of(entityVersion.getId()));
    }
    provenanceService.create(provenance);
  }
}
