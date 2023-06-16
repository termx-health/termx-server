package com.kodality.termserver.terminology.space;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
import com.kodality.termserver.ts.space.Space;
import com.kodality.termserver.ts.space.SpaceQueryParams;
import com.kodality.termserver.ts.space.diff.SpaceDiff;
import com.kodality.termserver.ts.space.diff.SpaceDiffRequest;
import com.kodality.termserver.ts.space.overview.SpaceOverviewRequest;
import com.kodality.termserver.ts.space.overview.SpaceOverviewResponse;
import com.kodality.termserver.ts.space.spacepackage.Package;
import com.kodality.termserver.terminology.space.spacepackage.PackageService;
import com.kodality.termserver.ts.space.spacepackage.PackageTransactionRequest;
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
@Controller("/spaces")
@RequiredArgsConstructor
public class SpaceController {
  private final SpaceService spaceService;
  private final PackageService packageService;
  private final SpaceDiffService diffService;
  private final SpaceImportService importService;
  private final SpaceOverviewService overviewService;

  private final ImportLogger importLogger;
  private static final String JOB_TYPE = "space-import";

  @Authorized(Privilege.P_EDIT)
  @Post()
  public Space create(@Valid @Body Space p) {
    p.setId(null);
    return spaceService.save(p);
  }

  @Authorized(Privilege.P_EDIT)
  @Put("{id}")
  public Space update(@Parameter Long id, @Valid @Body Space p) {
    p.setId(id);
    return spaceService.save(p);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("{id}")
  public Space load(@Parameter Long id) {
    return spaceService.load(id);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/{?params*}")
  public QueryResult<Space> search(SpaceQueryParams params) {
    return spaceService.query(params);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/{id}/packages")
  public List<Package> loadPackages(@Parameter Long id) {
    return packageService.loadAll(id);
  }

  @Authorized(Privilege.P_EDIT)
  @Post("/{id}/packages")
  public Package savePackage(@Parameter Long id, @Body PackageTransactionRequest request) {
    return packageService.save(request, id);
  }

  @Authorized(Privilege.P_VIEW)
  @Post("/overview")
  public SpaceOverviewResponse overview(@Valid @Body SpaceOverviewRequest request) {
    return overviewService.compose(request);
  }

  @Authorized(Privilege.P_VIEW)
  @Post("/diff")
  public SpaceDiff diff(@Valid @Body SpaceDiffRequest request) {
    return diffService.findDiff(request);
  }

  @Authorized(Privilege.P_EDIT)
  @Post(value = "/sync", consumes = MediaType.MULTIPART_FORM_DATA)
  public HttpResponse<?> importSpace(@Nullable Publisher<CompletedFileUpload> file) {
    JobLogResponse job = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Space import started");
        long start = System.currentTimeMillis();
        importService.importSpace(file);
        log.info("Space import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(job.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing space", e);
        importLogger.logImport(job.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing space", e);
        importLogger.logImport(job.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    return HttpResponse.ok(job);
  }
}
