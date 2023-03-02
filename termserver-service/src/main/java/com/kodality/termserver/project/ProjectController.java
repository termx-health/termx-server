package com.kodality.termserver.project;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.project.projectpackage.Package;
import com.kodality.termserver.project.projectpackage.PackageService;
import com.kodality.termserver.project.projectpackage.PackageTransactionRequest;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/projects")
@RequiredArgsConstructor
public class ProjectController {
  private final ProjectService projectService;
  private final PackageService packageService;
  private final ProjectOverviewService overviewService;

  @Authorized("*.Project.edit")
  @Post()
  public Project create(@Valid @Body Project p) {
    p.setId(null);
    return projectService.save(p);
  }

  @Authorized("*.Project.edit")
  @Put("{id}")
  public Project update(@Parameter Long id, @Valid @Body Project p) {
    p.setId(id);
    return projectService.save(p);
  }

  @Authorized("*.Project.view")
  @Get("{id}")
  public Project load(@Parameter Long id) {
    return projectService.load(id);
  }

  @Authorized("*.Project.view")
  @Get("/{?params*}")
  public QueryResult<Project> search(ProjectQueryParams params) {
    return projectService.query(params);
  }

  @Authorized("*.Project.view")
  @Get("/{id}/packages")
  public List<Package> loadPackages(@Parameter Long id) {
    return packageService.loadAll(id);
  }

  @Authorized("*.Project.edit")
  @Post("/{id}/packages")
  public Package savePackage(@Parameter Long id, @Body PackageTransactionRequest request) {
    return packageService.save(request, id);
  }

  @Authorized("*.Project.view")
  @Post("/overview")
  public ProjectOverviewResponse overview(@Valid @Body ProjectOverviewRequest request) {
    return overviewService.compose(request);
  }
}
