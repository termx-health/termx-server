package com.kodality.termserver.fhir.codesystem;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import io.micronaut.core.util.CollectionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFhirImportService {
  private final CodeSystemImportService importService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  public void importCodeSystems(Parameters parameters, List<String> successes, List<String> warnings) {
    List<String> urls = CollectionUtils.isNotEmpty(parameters.getParameter()) ?
        parameters.getParameter().stream().filter(p -> "url".equals(p.getName())).map(ParametersParameter::getValueString).toList() : Collections.emptyList();
    if (urls.isEmpty()) {
      throw ApiError.TE106.toApiException();
    }
    urls.forEach(url -> {
      try {
        importCodeSystemFromUrl(url);
        successes.add(String.format("CodeSystem from resource %s imported", url));
      } catch (Exception e) {
        warnings.add(String.format("CodeSystem from resource %s was not imported due to error: %s", url, e.getMessage()));
      }
    });
  }

  public void importCodeSystemFromUrl(String url) {
    String resource = getResource(url);
    importCodeSystem(resource);
  }

  public void importCodeSystem(String resource) {
    Resource res = FhirMapper.fromJson(resource, Resource.class);
    if ("Bundle".equals(res.getResourceType())) {
      Bundle bundle = FhirMapper.fromJson(resource, Bundle.class);
      bundle.getEntry().forEach(e -> importCodeSystem((CodeSystem) e.getResource()));
    } else {
      com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem =
          FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.CodeSystem.class);
      importCodeSystem(codeSystem);
    }
  }

  @Transactional
  public void importCodeSystem(com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    if (!ResourceType.codeSystem.equals(codeSystem.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    importService.importCodeSystem(CodeSystemFhirImportMapper.mapCodeSystem(codeSystem), associationTypes,
        PublicationStatus.active.equals(codeSystem.getStatus()));
  }

  private String getResource(String url) {
    log.info("Loading fhir code system from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.UTF_8);
  }

}
