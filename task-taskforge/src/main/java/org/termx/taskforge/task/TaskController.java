package org.termx.taskforge.task;

import com.kodality.commons.micronaut.rest.MultipartBodyReader;
import com.kodality.commons.micronaut.validation.BeanValidator;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.validation.Validated;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
public class TaskController {
  private final TaskService taskService;
  private final BeanValidator beanValidator;

  @Get("{id}")
  public Task load(@PathVariable Long id) {
    return taskService.load(id);
  }

  @Get("/{?params*}")
  public QueryResult<Task> search(TaskSearchParams params) {
    return taskService.search(params);
  }

  @Post
  public Task create(@Valid @Body Task task) {
    task.setId(null);
    return taskService.save(task, null);
  }

  @Put("{id}")
  public Task update(@PathVariable Long id, @Valid @Body Task task) {
    task.setId(id);
    return taskService.save(task, null);
  }

  @Post("{id}/status")
  public Task updateStatus(@PathVariable Long id, @Valid @Body Map<String, String> body) {
    taskService.updateStatus(id, body.get("status"));
    return taskService.load(id);
  }

  @Post(value = "/transaction", consumes = MediaType.MULTIPART_FORM_DATA)
  public Task saveTransaction(@Body MultipartBody partz) {
    MultipartBodyReader.MultipartBody body = MultipartBodyReader.readMultipart(partz);
    Task task = JsonUtil.fromJson(body.getTextParts().get("task"), Task.class);
    beanValidator.validate(task);
    return taskService.save(task, body.getAttachments());
  }

}
