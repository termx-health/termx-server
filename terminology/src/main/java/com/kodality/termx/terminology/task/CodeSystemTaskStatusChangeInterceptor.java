package com.kodality.termx.terminology.task;

import com.kodality.termx.task.Task;
import com.kodality.termx.task.TaskStatus;
import com.kodality.termx.task.TaskType;
import com.kodality.termx.task.api.TaskStatusChangeInterceptor;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemProvenanceService;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import java.util.List;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemTaskStatusChangeInterceptor implements TaskStatusChangeInterceptor {
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final CodeSystemProvenanceService provenanceService;
  public static final String CS_VERSION = "code-system-version";
  public static final String CS_ENTITY_VERSION = "concept-version";

  @Override
  @Transactional
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getWorkflow() == null || task.getContext() == null || !TaskStatus.accepted.equals(task.getStatus())) {
      return;
    }
    Optional<Long> csVersionId = task.getContext().stream().filter(ctx -> CS_VERSION.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId());
    Optional<Long> csEntityVersionId = task.getContext().stream().filter(ctx -> CS_ENTITY_VERSION.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId());

    if (task.getWorkflow().equals(TaskType.version_review) && csVersionId.isPresent()) {
      CodeSystemVersion csv = codeSystemVersionService.load(csVersionId.get());
      provenanceService.provenanceCodeSystemVersion("reviewed", csv.getCodeSystem(), csv.getVersion(), () -> {});
    }
    if (task.getWorkflow().equals(TaskType.version_approval) && csVersionId.isPresent()) {
      CodeSystemVersion csv = codeSystemVersionService.load(csVersionId.get());
      provenanceService.provenanceCodeSystemVersion("approved", csv.getCodeSystem(), csv.getVersion(),
          () -> codeSystemVersionService.activate(csv.getCodeSystem(), csv.getVersion()));
    }
    if (task.getWorkflow().equals(TaskType.concept_approval) && csEntityVersionId.isPresent()) {
      createConceptProvenance(csVersionId.orElse(null), csEntityVersionId.get());
    }
  }

  private void createConceptProvenance(Long csVersionId, Long csEntityVersionId) {
    provenanceService.provenanceEntityVersion("approved", csEntityVersionId, () -> {
      codeSystemEntityVersionService.activate(csEntityVersionId);
      return null;
    });
    if (csVersionId != null) {
      CodeSystemVersion version = codeSystemVersionService.load(csVersionId);
      provenanceService.provenanceCodeSystemVersion("approved", version.getCodeSystem(), version.getVersion(), () -> {
        codeSystemVersionService.linkEntityVersions(csVersionId, List.of(csEntityVersionId));
      });
    }
  }

}
