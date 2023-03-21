package com.kodality.termserver.terminology.project;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.ts.project.Project;
import com.kodality.termserver.ts.project.ProjectQueryParams;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ProjectService {
  private final ProjectRepository repository;
  private final UserPermissionService userPermissionService;

  @Transactional
  public Project save(Project project) {
    userPermissionService.checkPermitted(project.getCode(), "Project", "edit");
    repository.save(project);
    return project;
  }

  public Project load(Long id) {
    return repository.load(id);
  }

  public Project load(String code) {
    return repository.load(code);
  }

  public QueryResult<Project> query(ProjectQueryParams params) {
    return repository.query(params);
  }

}
