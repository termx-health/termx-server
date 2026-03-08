package org.termx.taskforge.task.execution;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
public class TaskExecutionController {
  private final TaskExecutionService taskExecutionService;

  @Get()
  public List<TaskExecution> load(@PathVariable Long taskId) {
    return taskExecutionService.loadAll(taskId);
  }

  @Post
  public TaskExecution create(@PathVariable Long taskId, @Valid @Body TaskExecution execution) {
    execution.setId(null);
    execution.setTaskId(taskId);
    return taskExecutionService.save(execution);
  }

  @Put("{id}")
  public TaskExecution update(@PathVariable Long taskId, @PathVariable Long id, @Valid @Body TaskExecution execution) {
    execution.setId(id);
    execution.setTaskId(taskId);
    return taskExecutionService.save(execution);
  }
}
