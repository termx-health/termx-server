package com.kodality.termx.taskflow;

import com.kodality.taskflow.api.TaskStatusChangeInterceptor;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskStatus;
import com.kodality.termx.snomed.concept.SnomedTranslationStatus;
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationActionService;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import static java.util.Map.entry;

@Singleton
@RequiredArgsConstructor
public class SnomedTaskStatusChangeInterceptor extends TaskStatusChangeInterceptor {
  private final SnomedTranslationActionService translationService;
  private static final Map<String, String> statusMap = Map.ofEntries(
      entry(TaskStatus.requested, SnomedTranslationStatus.proposed),
      entry(TaskStatus.accepted, SnomedTranslationStatus.active),
      entry(TaskStatus.rejected, SnomedTranslationStatus.deleted));

  @Override
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getContext() == null || task.getStatus() == null) {
      return;
    }
    task.getContext().stream().filter(ctx -> TaskFlowSnomedInterceptor.TASK_CTX_TYPE.equals(ctx.getType())).forEach(ctx -> {
      Optional<String> status = Optional.ofNullable(statusMap.get(task.getStatus()));
      status.ifPresent(s -> {
        translationService.updateStatus((Long) ctx.getId(), s);
        if (SnomedTranslationStatus.active.equals(s)) {
          translationService.addToBranch((Long) ctx.getId());
        }
      });
    });
  }
}
