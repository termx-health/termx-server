package com.kodality.termserver.observationdefinition;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
import com.kodality.termserver.observationdefintion.ObservationDefinition;
import com.kodality.termserver.observationdefintion.ObservationDefinitionImportRequest;
import com.kodality.termserver.observationdefintion.ObservationDefinitionSearchParams;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.concurrent.CompletableFuture;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/observation-definitions")
@RequiredArgsConstructor
public class ObservationDefinitionController {
  private final ObservationDefinitionService observationDefinitionService;
  private final ObservationDefinitionImportService observationDefinitionImportService;

  private final ImportLogger importLogger;

  @Authorized(Privilege.OBS_DEF_EDIT)
  @Post()
  public ObservationDefinition create(@Body @Valid ObservationDefinition def) {
    def.setId(null);
    observationDefinitionService.save(def);
    return load(def.getId());
  }

  @Authorized(Privilege.OBS_DEF_EDIT)
  @Put("/{id}")
  public ObservationDefinition update(@Parameter Long id, @Body @Valid ObservationDefinition def) {
    def.setId(id);
    observationDefinitionService.save(def);
    return load(def.getId());
  }

  @Authorized(Privilege.OBS_DEF_VIEW)
  @Get("/{id}")
  public ObservationDefinition load(@Parameter Long id) {
    ObservationDefinition def = observationDefinitionService.load(id);
    if (def == null) {
      throw new NotFoundException("Observation definition", id);
    }
    return def;
  }

  @Authorized(Privilege.OBS_DEF_VIEW)
  @Get("/{?params*}")
  public QueryResult<ObservationDefinition> search(ObservationDefinitionSearchParams params) {
    return observationDefinitionService.search(params);
  }

  @Authorized(Privilege.OBS_DEF_EDIT)
  @Post("/import")
  public JobLogResponse importDefinitions(@Body @Valid ObservationDefinitionImportRequest request) {
    JobLogResponse jobLogResponse = importLogger.createJob("OBS-DEF-IMPORT");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Observation definition import started");
        long start = System.currentTimeMillis();
        observationDefinitionImportService.importDefinitions(request);
        log.info("Observation definition import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing observation definition", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing observation definition", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.EI000.toApiException());
      }
    }));
    return jobLogResponse;
  }

}
