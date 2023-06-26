package com.kodality.termserver.fhir.valueset.operations;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.fhir.valueset.ValueSetFhirImportService;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;

@Slf4j
@Factory
@RequiredArgsConstructor
public class ValueSetSyncOperation implements TypeOperationDefinition {
  private final ImportLogger importLogger;
  private final ValueSetFhirImportService importService;

  private static final String JOB_TYPE = "FHIR-VS";

  public String getResourceType() {
    return ResourceType.ValueSet.name();
  }

  public String getOperationName() {
    return "sync";
  }

  public ResourceContent run(ResourceContent res) {
    Parameters req = FhirMapper.fromJson(res.getValue(), Parameters.class);

    List<String> urls = CollectionUtils.isEmpty(req.getParameter()) ? List.of() :
        req.getParameter().stream().filter(p -> "url".equals(p.getName())).map(ParametersParameter::getValueString).toList();
    if (urls.isEmpty()) {
      throw ApiError.TE106.toApiException();
    }

    JobLogResponse jobLogResponse = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        List<String> successes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        log.info("Fhir value set import started");
        long start = System.currentTimeMillis();
        urls.forEach(url -> {
          try {
            importService.importValueSetFromUrl(url);
            successes.add(String.format("ValueSet from resource %s imported", url));
          } catch (Exception e) {
            String warning = String.format("ValueSet from resource %s was not imported due to error: %s", url, e.getMessage());
            log.error(warning, e);
            warnings.add(warning);
          }
        });
        log.info("Fhir value set import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId(), successes, warnings);
      } catch (ApiClientException e) {
        log.error("Error while importing fhir value set", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing fhir value set (TE700)", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    Parameters resp = new Parameters().setParameter(List.of(new ParametersParameter("jobId").setValueString(jobLogResponse.getJobId().toString())));
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

}
