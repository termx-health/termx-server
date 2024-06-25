package com.kodality.termx.terminology.task;

import com.kodality.termx.task.Task;
import com.kodality.termx.task.TaskStatus;
import com.kodality.termx.task.TaskType;
import com.kodality.termx.task.api.TaskStatusChangeInterceptor;
import com.kodality.termx.terminology.terminology.valueset.ValueSetProvenanceService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetTaskStatusChangeInterceptor implements TaskStatusChangeInterceptor {
  private final ValueSetProvenanceService provenanceService;
  private final ValueSetVersionService valueSetVersionService;
  public static final String VS_VERSION = "value-set-version";

  @Override
  @Transactional
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getWorkflow() == null || task.getContext() == null || !TaskStatus.accepted.equals(task.getStatus())) {
      return;
    }
    Optional<Long> vsVersionId = task.getContext().stream().filter(ctx -> VS_VERSION.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId());

    if (task.getWorkflow().equals(TaskType.version_review) && vsVersionId.isPresent()) {
      ValueSetVersion vsv = valueSetVersionService.load(vsVersionId.get());
      provenanceService.provenanceValueSetVersion("reviewed",vsv.getValueSet(), vsv.getVersion(), () -> {});
    }
    if (task.getWorkflow().equals(TaskType.version_approval) && vsVersionId.isPresent()) {
      ValueSetVersion vsv = valueSetVersionService.load(vsVersionId.get());
      provenanceService.provenanceValueSetVersion("approved", vsv.getValueSet(), vsv.getVersion(),
          () -> valueSetVersionService.activate(vsv.getValueSet(), vsv.getVersion()));
    }
  }
}
