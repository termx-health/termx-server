package com.kodality.termx.terminology.terminology.mapset;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.terminology.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.terminology.terminology.mapset.association.MapSetAutomapService;
import com.kodality.termx.terminology.terminology.mapset.concept.MapSetConceptService;
import com.kodality.termx.terminology.terminology.mapset.statistics.MapSetStatisticsService;
import com.kodality.termx.terminology.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetAutomapRequest;
import com.kodality.termx.ts.mapset.MapSetConcept;
import com.kodality.termx.ts.mapset.MapSetConceptQueryParams;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.mapset.MapSetTransactionRequest;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersionQueryParams;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
@Controller("/ts/map-sets")
@RequiredArgsConstructor
public class MapSetController {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetConceptService mapSetConceptService;
  private final MapSetAssociationService mapSetAssociationService;
  private final MapSetAutomapService mapSetAutomapService;
  private final MapSetStatisticsService mapSetStatisticsService;
  private final MapSetProvenanceService provenanceService;

  private final ImportLogger importLogger;

  //----------------MapSet----------------

  @Authorized(Privilege.MS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<MapSet> queryMapSets(MapSetQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.MS_VIEW));
    return mapSetService.query(params);
  }

  @Authorized(Privilege.MS_VIEW)
  @Get(uri = "/{mapSet}{?decorate}")
  public MapSet getMapSet(@PathVariable String mapSet, Optional<Boolean> decorate) {
    return mapSetService.load(mapSet, decorate.orElse(false))
        .orElseThrow(() -> new NotFoundException("MapSet not found: " + mapSet));
  }

  @Authorized(Privilege.MS_EDIT)
  @Post("/transaction")
  public HttpResponse<?> saveMapSetTransaction(@Body @Valid MapSetTransactionRequest request) {
    SessionStore.require().checkPermitted(request.getMapSet().getId(), Privilege.VS_EDIT);
    provenanceService.provenanceMapSetTransaction("save", request, () -> {
      mapSetService.save(request);
    });
    return HttpResponse.created(request.getMapSet());
  }

  @Authorized(Privilege.MS_EDIT)
  @Post(uri = "/{mapSet}/change-id")
  public HttpResponse<?> changeMapSetId(@PathVariable String mapSet, @Valid @Body Map<String, String> body) {
    String newId = body.get("id");
    mapSetService.changeId(mapSet, newId);
    provenanceService.create(new Provenance("change-id", "MapSet", newId).setChanges(Map.of("id", ProvenanceChange.of(mapSet, newId))));
    return HttpResponse.ok();
  }


  @Authorized(Privilege.MS_PUBLISH)
  @Delete(uri = "/{mapSet}")
  public HttpResponse<?> deleteMapSet(@PathVariable String mapSet) {
    mapSetService.cancel(mapSet);
    provenanceService.create(new Provenance("delete", "MapSet", mapSet));
    return HttpResponse.ok();
  }

  //----------------MapSet Version----------------

  @Authorized(Privilege.MS_VIEW)
  @Get(uri = "/{mapSet}/versions{?params*}")
  public QueryResult<MapSetVersion> queryMapSetVersions(@PathVariable String mapSet, MapSetVersionQueryParams params) {
    params.setMapSet(mapSet);
    return mapSetVersionService.query(params);
  }

  @Authorized(Privilege.MS_VIEW)
  @Get(uri = "/{mapSet}/versions/{version}")
  public MapSetVersion getMapSetVersion(@PathVariable String mapSet, @PathVariable String version) {
    return mapSetVersionService.load(mapSet, version)
        .orElseThrow(() -> new NotFoundException("Map set version not found: " + version));
  }

  @Authorized(Privilege.MS_EDIT)
  @Post(uri = "/{mapSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable String mapSet, @Body @Valid MapSetVersion mapSetVersion) {
    mapSetVersion.setId(null);
    mapSetVersion.setMapSet(mapSet);
    provenanceService.provenanceMapSetVersion("save", mapSet, mapSetVersion.getVersion(), () -> {
      mapSetVersionService.save(mapSetVersion);
    });
    return HttpResponse.created(mapSetVersion);
  }

  @Authorized(Privilege.MS_EDIT)
  @Put(uri = "/{mapSet}/versions/{version}")
  public HttpResponse<?> updateVersion(@PathVariable String mapSet, @PathVariable String version, @Body @Valid MapSetVersion mapSetVersion) {
    mapSetVersion.setVersion(version);
    mapSetVersion.setMapSet(mapSet);
    provenanceService.provenanceMapSetVersion("save", mapSet, mapSetVersion.getVersion(), () -> {
      mapSetVersionService.save(mapSetVersion);
    });
    return HttpResponse.created(mapSetVersion);
  }

  @Authorized(Privilege.MS_PUBLISH)
  @Post(uri = "/{mapSet}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String mapSet, @PathVariable String version) {
    provenanceService.provenanceMapSetVersion("activate", mapSet, version, () -> {
      mapSetVersionService.activate(mapSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.MS_PUBLISH)
  @Post(uri = "/{mapSet}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable String mapSet, @PathVariable String version) {
    provenanceService.provenanceMapSetVersion("retire", mapSet, version, () -> {
      mapSetVersionService.retire(mapSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{mapSet}/versions/{version}/draft")
  public HttpResponse<?> saveAsDraftMapSetVersion(@PathVariable String mapSet, @PathVariable String version) {
    provenanceService.provenanceMapSetVersion("save", mapSet, version, () -> {
      mapSetVersionService.saveAsDraft(mapSet, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Delete(uri = "/{mapSet}/versions/{version}")
  public HttpResponse<?> deleteMapSetVersion(@PathVariable String mapSet, @PathVariable String version) {
    MapSetVersion msv = mapSetVersionService.load(mapSet, version).orElseThrow();
    mapSetVersionService.cancel(msv.getId());
    provenanceService.create(new Provenance("deleted", "MapSetVersion", msv.getId().toString(), msv.getVersion())
        .addContext("part-of", "MapSet", msv.getMapSet()));
    return HttpResponse.ok();
  }


  //----------------MapSet Statistics----------------

  @Authorized(Privilege.MS_VIEW)
  @Post(uri = "/{mapSet}/versions/{version}/reload-statistics-async")
  public JobLogResponse reloadStatisticsAsync(@PathVariable String mapSet, @PathVariable String version) {
    JobLogResponse jobLogResponse = importLogger.createJob(mapSet, "map-set-statistics-reload");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("MapSet '{}' statistics calculation started", mapSet);
        long start = System.currentTimeMillis();
        mapSetStatisticsService.calculate(mapSet, version);
        log.info("MapSet '{}' statistics calculation took {}", mapSet, (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while MapSet '{}' statistics calculation", mapSet, e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while MapSet '{}' statistics calculation ", mapSet, e);
        importLogger.logImport(jobLogResponse.getJobId(), null, null, List.of(ExceptionUtils.getStackTrace(e)));
      }
    }));
    return jobLogResponse;
  }


  //----------------MapSet Concept----------------

  @Authorized(Privilege.MS_VIEW)
  @Get(uri = "/{mapSet}/versions/{version}/concepts{?params*}")
  public QueryResult<MapSetConcept> queryMapSetConcepts(@PathVariable String mapSet, @PathVariable String version, MapSetConceptQueryParams params) {
    return mapSetConceptService.query(mapSet, version, params);
  }


  //----------------MapSet Association----------------

  @Authorized(Privilege.MS_VIEW)
  @Get(uri = "/{mapSet}/associations{?params*}")
  public QueryResult<MapSetAssociation> queryAssociations(@PathVariable String mapSet, MapSetAssociationQueryParams params) {
    params.setMapSet(mapSet);
    return mapSetAssociationService.query(params);
  }

  @Authorized(Privilege.MS_EDIT)
  @Post(uri = "/{mapSet}/versions/{version}/associations")
  public HttpResponse<?> createAssociation(@PathVariable String mapSet, @PathVariable String version, @Body @Valid MapSetAssociation association) {
    association.setId(null);
    provenanceService.provenanceMapSetVersion("create-association", mapSet, version, () -> {
      mapSetAssociationService.save(association, mapSet, version);
    });
    return HttpResponse.created(association);
  }

  @Authorized(Privilege.MS_EDIT)
  @Put(uri = "/{mapSet}/versions/{version}/associations/{id}")
  public HttpResponse<?> updateAssociation(@PathVariable String mapSet, @PathVariable String version, @PathVariable Long id, @Body @Valid MapSetAssociation association) {
    association.setId(id);
    provenanceService.provenanceMapSetVersion("update-association", mapSet, version, () -> {
      mapSetAssociationService.save(association, mapSet, version);
    });
    return HttpResponse.created(association);
  }

  @Authorized(Privilege.MS_EDIT)
  @Post(uri = "/{mapSet}/versions/{version}/associations-batch")
  public HttpResponse<?> saveAssociations(@PathVariable String mapSet, @PathVariable String version, @Body @Valid Map<String,List<MapSetAssociation>> associations) {
    provenanceService.provenanceMapSetVersion("save-association-batch", mapSet, version, () -> {
      mapSetAssociationService.batchSave(associations.getOrDefault("batch", List.of()), mapSet, version);
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.MS_EDIT)
  @Post(uri = "/{mapSet}/versions/{version}/associations/verify")
  public HttpResponse<?> verifyAssociations(@PathVariable String mapSet, @PathVariable String version, @Body @Valid Map<String, List<Long>> request) {
    provenanceService.provenanceMapSetVersion("verify-association-batch", mapSet, version, () -> {
      mapSetAssociationService.verify(request.get("verifiedIds"), request.get("unVerifiedIds"), mapSet);
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.MS_EDIT)
  @Post(uri = "/{mapSet}/versions/{version}/associations/unmap")
  public HttpResponse<?> unmapAssociations(@PathVariable String mapSet, @PathVariable String version, @Body @Valid Map<String, List<Long>> request) {
    provenanceService.provenanceMapSetVersion("unmap-association-batch", mapSet, version, () -> {
      mapSetAssociationService.cancel(request.get("ids"), mapSet, version);
    });
    return HttpResponse.ok();
  }

  //----------------Provenances----------------

  @Authorized(Privilege.MS_VIEW)
  @Get(uri = "/{mapSet}/provenances")
  public List<Provenance> queryProvenances(@PathVariable String mapSet, @Nullable @QueryValue String version) {
    return provenanceService.find(mapSet, version);
  }

  @Authorized(Privilege.MS_EDIT)
  @Post(uri = "/{mapSet}/versions/{version}/associations/automap")
  public JobLogResponse automapAssociations(@PathVariable String mapSet, @PathVariable String version, @Body @Valid MapSetAutomapRequest request) {
    JobLogResponse jobLogResponse = importLogger.createJob(mapSet, "map-set-automap");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("MapSet '{}' automap started", mapSet);
        long start = System.currentTimeMillis();
        mapSetAutomapService.automap(mapSet, version, request);
        log.info("MapSet '{}' automap took {}", mapSet, (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while MapSet '{}' automap", mapSet, e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while MapSet '{}' automap ", mapSet, e);
        importLogger.logImport(jobLogResponse.getJobId(), null, null, List.of(ExceptionUtils.getStackTrace(e)));
      }
    }));
    return jobLogResponse;
  }
}
