package com.kodality.termx.core.sys.space;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.SpaceQueryParams;
import com.kodality.termx.sys.space.overview.SpaceOverviewResponse;
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
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/spaces")
@RequiredArgsConstructor
public class SpaceController {
  private final SpaceService spaceService;
  private final SpaceSyncService syncService;
  private final SpaceResourceDiffService diffService;
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
  public SpaceOverviewResponse overview(@PathVariable Long id, @Nullable @QueryValue String packageCode, @Nullable @QueryValue String version) {
    return overviewService.compose(id, packageCode, version);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{id}/diff")
  public HttpResponse<?> diff(@PathVariable Long id, @Nullable @QueryValue String packageCode, @Nullable @QueryValue String version) {
    LorqueProcess lorqueProcess = diffService.findDiff(id, packageCode, version);
    return HttpResponse.accepted().body(lorqueProcess);
  }

  @Authorized(Privilege.S_EDIT)
  @Post("/{id}/sync")
  public HttpResponse<?> syncResources(@PathVariable Long id, @Body Map<String, Object> request) {
    JobLogResponse response = syncService.syncResources(id,
        (String) request.getOrDefault("packageCode", null),
        (String) request.getOrDefault("version", null),
        (boolean) request.getOrDefault("clearSync", false));
    return HttpResponse.accepted().body(response);
  }

  @Authorized(privilege = Privilege.S_EDIT)
  @Post(value = "/sync", consumes = MediaType.MULTIPART_FORM_DATA)
  public HttpResponse<?> importSpace(@Nullable Publisher<CompletedFileUpload> file) {
    if (file == null) {
      return HttpResponse.badRequest();
    }
    JobLogResponse job = importLogger.runJob(JOB_TYPE, file, importService::importSpace);
    return HttpResponse.ok(job);
  }
}
