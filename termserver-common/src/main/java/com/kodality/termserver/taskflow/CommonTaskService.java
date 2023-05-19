package com.kodality.termserver.taskflow;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.LocalizedName;
import com.kodality.taskflow.space.Space;
import com.kodality.taskflow.space.SpaceService;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskPriority;
import com.kodality.taskflow.task.Task.TaskStatus;
import com.kodality.taskflow.task.TaskSearchParams;
import com.kodality.taskflow.task.TaskService;
import com.kodality.termserver.ts.Language;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@RequiredArgsConstructor
public class CommonTaskService {
  private final TaskService taskService;
  private final SpaceService spaceService;

  private final static String SPACE_CODE = "kts";
  private final static String INSTITUTION = "1";

  public Map<String, List<CodeName>> findTasks(String context) {
    if (StringUtils.isEmpty(context)) {
      return Map.of();
    }
    TaskSearchParams params = new TaskSearchParams();
    params.setContext(context);
    params.all();
    List<Task> tasks = taskService.search(params).getData();
    return tasks.stream()
        .filter(t -> t.getContext() != null)
        .collect(Collectors.groupingBy(t -> t.getContext().stream().map(c -> c.getType() + "|" + c.getId()).collect(Collectors.joining(","))))
        .entrySet().stream()
        .map(es -> Pair.of(es.getKey(), es.getValue().stream()
            .map(t -> new CodeName().setId(t.getId()).setCode(t.getNumber()).setNames(new LocalizedName(Map.of(Language.en, t.getTitle())))).toList()))
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  public void createTask(Task task) {
    String context = task.getContext().stream().map(ctx -> ctx.getType() + "|" + ctx.getId()).collect(Collectors.joining(","));
    List<CodeName> tasks = findTasks(context).values().stream().flatMap(Collection::stream).toList();
    if (CollectionUtils.isNotEmpty(tasks)) {
      return;
    }
    task.setSpaceId(getSpaceId());
    task.setStatus(task.getStatus() == null ? TaskStatus.requested : task.getStatus());
    task.setPriority(task.getPriority() == null ? TaskPriority.routine : task.getPriority());
    taskService.save(task, null);
  }


  private Long getSpaceId() {
    Space space = spaceService.load(SPACE_CODE, INSTITUTION);
    if (space == null) {
      return createSpace();
    }
    return space.getId();

  }

  private Long createSpace() {
    Space space = new Space();
    space.setCode(SPACE_CODE);
    space.setNames(new LocalizedName(Map.of(Language.en, "Kodality Terminology Service")));
    space.setInstitution(INSTITUTION);
    // fixme: marina
    // space.setTransitions(List.of(
    //     new SpaceTaskTransition().setFrom(null).setTo(TaskStatus.draft),
    //     new SpaceTaskTransition().setFrom(null).setTo(TaskStatus.requested),
    //     new SpaceTaskTransition().setFrom(TaskStatus.draft).setTo(TaskStatus.requested),
    //     new SpaceTaskTransition().setFrom(TaskStatus.requested).setTo(TaskStatus.received),
    //     new SpaceTaskTransition().setFrom(TaskStatus.received).setTo(TaskStatus.accepted),
    //     new SpaceTaskTransition().setFrom(TaskStatus.received).setTo(TaskStatus.rejected)
    // ));
    return spaceService.save(space).getId();
  }
}
