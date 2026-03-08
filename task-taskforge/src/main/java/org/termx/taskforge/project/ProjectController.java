package org.termx.taskforge.project;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.validation.Validated;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
public class ProjectController {
  private final ProjectService projectService;

  @Get()
  public List<Project> search() {
    return projectService.loadAll();
  }

  @Get("{id}")
  public Project load(@PathVariable Long id) {
    return projectService.load(id);
  }

}
