package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
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
public class CodeSystemSyncOperation implements TypeOperationDefinition {
  private final ImportLogger importLogger;
  private final CodeSystemFhirImportService importService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "sync";
  }

  public ResourceContent run(ResourceContent c) {
    Parameters req = FhirMapper.fromJson(c.getValue(), Parameters.class);

    List<ParametersParameter> resources = CollectionUtils.isEmpty(req.getParameter()) ? List.of() :
        req.getParameter().stream().filter(p -> "resources".equals(p.getName())).toList();
    if (resources.isEmpty()) {
      throw ApiError.TE106.toApiException();
    }
    resources.forEach(res -> {
      String id = res.findPart("id").map(ParametersParameter::getValueString).orElseThrow();
      SessionStore.require().checkPermitted(id, Privilege.CS_EDIT);
    });

    JobLogResponse jobLogResponse = importLogger.createJob("FHIR-CS");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        List<String> successes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        log.info("Fhir code system import started");
        long start = System.currentTimeMillis();

        resources.forEach(res -> {
          String url = res.findPart("url").map(ParametersParameter::getValueString).orElseThrow();
          String id = res.findPart("id").map(ParametersParameter::getValueString).orElseThrow();
          try {
            importService.importCodeSystemFromUrl(url, id);
            successes.add(String.format("CodeSystem from resource %s imported", url));
          } catch (Exception e) {
            warnings.add(String.format("CodeSystem from resource %s was not imported due to error: %s", url, e.getMessage()));
          }
        });

        log.info("Fhir code system import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId(), successes, warnings);
      } catch (ApiClientException e) {
        log.error("Error while importing fhir code system", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing fhir code system (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    Parameters resp = new Parameters().addParameter(new ParametersParameter("jobId").setValueString(jobLogResponse.getJobId().toString()));
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

}
