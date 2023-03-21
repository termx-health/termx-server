package com.kodality.termserver.terminology.project;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.job.logger.ImportLogger;
import com.kodality.termserver.ts.project.Project;
import com.kodality.termserver.ts.project.ProjectQueryParams;
import com.kodality.termserver.ts.project.diff.ProjectDiff;
import com.kodality.termserver.ts.project.diff.ProjectDiffRequest;
import com.kodality.termserver.ts.project.overview.ProjectOverviewRequest;
import com.kodality.termserver.ts.project.overview.ProjectOverviewResponse;
import com.kodality.termserver.ts.project.projectpackage.Package;
import com.kodality.termserver.terminology.project.projectpackage.PackageService;
import com.kodality.termserver.ts.project.projectpackage.PackageTransactionRequest;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.multipart.CompletedFileUpload;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/projects")
@RequiredArgsConstructor
public class ProjectController {
  private final ProjectService projectService;
  private final PackageService packageService;
  private final ProjectDiffService diffService;
  private final ProjectImportService importService;
  private final ProjectOverviewService overviewService;

  private final ImportLogger importLogger;
  private static final String JOB_TYPE = "project-import";

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

  @Authorized("*.Project.view")
  @Post("/diff")
  public ProjectDiff diff(@Valid @Body ProjectDiffRequest request) {
    return diffService.findDiff(request);
  }

  @Authorized("*.Project.edit")
  @Post(value = "/sync", consumes = MediaType.MULTIPART_FORM_DATA)
  public HttpResponse<?> importProject(@Nullable Publisher<CompletedFileUpload> file) {
    JobLogResponse job = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Project import started");
        long start = System.currentTimeMillis();
        importService.importProject(file);
        log.info("Project import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(job.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing project", e);
        importLogger.logImport(job.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing project", e);
        importLogger.logImport(job.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    return HttpResponse.ok(job);
  }
}
