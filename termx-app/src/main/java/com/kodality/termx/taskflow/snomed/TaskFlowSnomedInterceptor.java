package com.kodality.termx.taskflow.snomed;

import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskContextItem;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.snomed.SnomedInterceptor;
import com.kodality.termx.taskflow.TaskFlowService;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskFlowSnomedInterceptor extends SnomedInterceptor {
  private final TaskFlowService taskFlowService;

  public static final String TASK_CTX_TYPE = "snomed-translation";
  public static final String TASK_WORKFLOW = "concept-approval";

  @Override
  public void afterTranslationSave(String conceptId, SnomedTranslation t) {
    Task task = new Task();
    task.setTitle(String.format("%s concept translation validation", conceptId));
    task.setContent(String.format("Module: %s \nLanguage: %s \nTerm: %s \nType: %s \nAcceptability: %s",
        t.getModule(), t.getLanguage(), t.getTerm(), t.getType(), t.getAcceptability()));
    task.setContext(List.of(new TaskContextItem().setId(t.getId()).setType(TASK_CTX_TYPE)));
    taskFlowService.createTask(task, TASK_WORKFLOW);
  }

  @Override
  public void afterTranslationsCancel(List<Long> ids) {
    String ctx = ids.stream().map(id -> TASK_CTX_TYPE + "|" + id).collect(Collectors.joining(","));
    taskFlowService.cancelTasks(ctx);
  }
}
