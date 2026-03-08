package org.termx.taskforge.task.activity;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
public class TaskActivityController {
  private final TaskActivityService taskActivityService;

  @Get("/{?params*}")
  public List<TaskActivity> load(@PathVariable Long id, TaskActivitySearchParams params) {
    params.setTaskIds(id.toString());
    return taskActivityService.search(params);
  }

  @Post
  public TaskActivity create(@PathVariable Long id, @Valid @Body TaskActivity activity) {
    activity.setId(null);
    activity.setTaskId(id);
    return taskActivityService.save(activity);
  }
}
