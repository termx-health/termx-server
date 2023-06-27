package com.kodality.termserver.fhir.conceptmap.operations;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapSyncOperation implements TypeOperationDefinition {
  private final ImportLogger importLogger;
  private final ConceptMapFhirImportService importService;

  public String getResourceType() {
    return ResourceType.ConceptMap.name();
  }

  public String getOperationName() {
    return "$sync";
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(Parameters req) {
    List<ParametersParameter> resources = CollectionUtils.isEmpty(req.getParameter()) ? List.of() :
        req.getParameter().stream().filter(p -> "resources".equals(p.getName())).toList();
    if (resources.isEmpty()) {
      throw ApiError.TE106.toApiException();
    }


    JobLogResponse jobLogResponse = importLogger.createJob("FHIR-MS");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        List<String> successes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        log.info("Fhir map set import started");
        long start = System.currentTimeMillis();
        resources.forEach(res -> {
          String url = res.findPart("url").map(ParametersParameter::getValueString).orElseThrow();
          String id = res.findPart("id").map(ParametersParameter::getValueString).orElseThrow();
          try {
            importService.importMapSet(url, id);
            successes.add(String.format("ConceptMap from resource %s imported", url));
          } catch (Exception e) {
            warnings.add(String.format("ConceptMap from resource %s was not imported due to error: %s", url, e.getMessage()));
          }
        });
        log.info("Fhir map set import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId(), successes, warnings);
      } catch (ApiClientException e) {
        log.error("Error while importing fhir map set", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing fhir map set (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    return new Parameters().addParameter(
        new ParametersParameter().setName("jobId").setValueString(jobLogResponse.getJobId().toString())
    );
  }
}
