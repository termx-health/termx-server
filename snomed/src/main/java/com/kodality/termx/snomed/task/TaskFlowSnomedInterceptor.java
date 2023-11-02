package com.kodality.termx.snomed.task;

import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.description.SnomedDescriptionSearchParams;
import com.kodality.termx.snomed.snomed.SnomedInterceptor;
import com.kodality.termx.snomed.snomed.SnomedService;
import com.kodality.termx.task.Task;
import com.kodality.termx.task.Task.TaskContextItem;
import com.kodality.termx.task.TaskPriority;
import com.kodality.termx.task.TaskService;
import com.kodality.termx.task.TaskStatus;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskFlowSnomedInterceptor extends SnomedInterceptor {
  private final TaskService taskService;
  private final SnomedService snomedService;

  public static final String TASK_CTX_TYPE = "snomed-translation";
  public static final String TASK_WORKFLOW = "concept-approval";

  @Override
  public void afterTranslationSave(String conceptId, SnomedTranslation t) {
    List<SnomedDescription> snomedDescriptions = snomedService.searchDescriptions(t.getBranch(), new SnomedDescriptionSearchParams().setConceptId(conceptId).setAll(true));

    Task task = taskService.findTasks(TASK_CTX_TYPE + "|" + t.getId()).stream().findFirst()
        .orElseGet(() -> new Task()
            .setStatus(TaskStatus.requested)
            .setPriority(TaskPriority.routine)
        );
    task.setWorkflow(TASK_WORKFLOW);
    task.setTitle(String.format("%s concept translation validation", conceptId));
    task.setContent(String.format("Concept [%s](concept:snomed-ct|%s) \n\n" +
        snomedDescriptions.stream().map(d -> d.getLang() + ": " + d.getTerm()).collect(Collectors.joining("\n")) +
            "\n\nModule: %s \nBranch: %s \nLanguage: %s \nTerm: %s \nType: %s \nAcceptability: %s",
        conceptId, conceptId,
        t.getModule(), t.getBranch(), t.getLanguage(), t.getTerm(), t.getType(), t.getAcceptability()));
    task.setContext(List.of(new TaskContextItem().setId(t.getId()).setType(TASK_CTX_TYPE)));
    taskService.saveTask(task);
  }

  @Override
  public void afterTranslationsCancel(List<Long> ids) {
    String ctx = ids.stream().map(id -> TASK_CTX_TYPE + "|" + id).collect(Collectors.joining(","));
    taskService.cancelTasks(ctx);
  }
}
