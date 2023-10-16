package com.kodality.termx.core.sys.space;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.SpaceQueryParams;
import com.kodality.termx.sys.space.diff.SpaceDiff;
import com.kodality.termx.sys.space.overview.SpaceOverviewResponse;
import com.kodality.termx.core.utils.FileUtil;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.reactivex.Flowable;
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
  private final SpaceDiffService diffService;
  private final SpaceImportService importService;
  private final SpaceOverviewService overviewService;

  private final ImportLogger importLogger;
  private static final String JOB_TYPE = "space-import";

  @Authorized(privilege = Privilege.S_EDIT)
  @Post()
  public Space create(@Valid @Body Space p) {
    p.setId(null);
    return spaceService.save(p);
  }

  @Authorized(Privilege.S_EDIT)
  @Put("{id}")
  public Space update(@PathVariable Long id, @Valid @Body Space p) {
    p.setId(id);
    return spaceService.save(p);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("{id}")
  public Space load(@PathVariable Long id) {
    return spaceService.load(id);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{?params*}")
  public QueryResult<Space> search(SpaceQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.S_VIEW, Long::valueOf));
    return spaceService.query(params);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{id}/overview")
  public SpaceOverviewResponse overview(@PathVariable Long id, @QueryValue String packageCode, @QueryValue String version) {
    return overviewService.compose(id, packageCode, version);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{id}/diff")
  public SpaceDiff diff(@PathVariable Long id, @QueryValue String packageCode, @QueryValue String version) {
    return diffService.findDiff(id, packageCode, version);
  }

  @Authorized(privilege = Privilege.S_EDIT)
  @Post(value = "/sync", consumes = MediaType.MULTIPART_FORM_DATA)
  public HttpResponse<?> importSpace(@Nullable Publisher<CompletedFileUpload> file) {
    //TODO: auth?
    JobLogResponse job = importLogger.createJob(JOB_TYPE);
    if (file == null) {
      return HttpResponse.badRequest();
    }


    String yaml = new String(FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Space import started");
        long start = System.currentTimeMillis();
        importService.importSpace(yaml);
        log.info("Space import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(job.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing space", e);
        importLogger.logImport(job.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing space", e);
        importLogger.logImport(job.getJobId(), ApiError.TC106.toApiException());
      }
    }));
    return HttpResponse.ok(job);
  }
}
