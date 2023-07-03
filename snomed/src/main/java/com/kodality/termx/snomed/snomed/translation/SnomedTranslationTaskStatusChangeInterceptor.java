package com.kodality.termx.snomed.snomed.translation;

import com.kodality.taskflow.api.TaskStatusChangeInterceptor;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskStatus;
import com.kodality.termx.snomed.concept.SnomedTranslationStatus;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import static java.util.Map.entry;

@Singleton
@RequiredArgsConstructor
public class SnomedTranslationTaskStatusChangeInterceptor extends TaskStatusChangeInterceptor {
  private final SnomedTranslationRepository translationService;
  private static final Map<String, String> statusMap = Map.ofEntries(
      entry(TaskStatus.draft, SnomedTranslationStatus.proposed),
      entry(TaskStatus.requested, SnomedTranslationStatus.proposed),
      entry(TaskStatus.received, SnomedTranslationStatus.proposed),
      entry(TaskStatus.accepted, SnomedTranslationStatus.active),
      entry(TaskStatus.rejected, SnomedTranslationStatus.deleted),
      entry(TaskStatus.ready, SnomedTranslationStatus.active),
      entry(TaskStatus.cancelled, SnomedTranslationStatus.deleted),
      entry(TaskStatus.in_progress, SnomedTranslationStatus.proposed),
      entry(TaskStatus.on_hold, SnomedTranslationStatus.proposed),
      entry(TaskStatus.failed, SnomedTranslationStatus.deleted),
      entry(TaskStatus.completed, SnomedTranslationStatus.active),
      entry(TaskStatus.error, SnomedTranslationStatus.deleted));

  @Override
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getId() == null || task.getContext() == null || task.getStatus() == null) {
      return;
    }
    task.getContext().stream().filter(ctx -> SnomedTranslationService.TASK_CTX_TYPE.equals(ctx.getType())).forEach(ctx -> {
      Optional<String> status = Optional.ofNullable(statusMap.get(task.getStatus()));
      status.ifPresent(s -> translationService.updateStatus((Long) ctx.getId(), s));
    });
  }
}
