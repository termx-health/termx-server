package com.kodality.termserver.fhir.conceptmap;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetQueryParams;
import com.kodality.termserver.ts.mapset.MapSetService;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ConceptMapFhirService {
  private final ConceptMapFhirMapper mapper;
  private final MapSetService mapSetService;

  public Parameters translate(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("code").isEmpty() || fhirParams.getFirst("system").isEmpty()) {
      return new Parameters();
    }

    MapSetQueryParams msParams = new MapSetQueryParams()
        .setUri(fhirParams.getFirst("uri").orElse(null))
        .setVersionVersion(fhirParams.getFirst("conceptMapVersion").orElse(null))
        .setAssociationSourceCode(fhirParams.getFirst("code").orElse(null))
        .setAssociationSourceSystemUri(fhirParams.getFirst("system").orElse(null))
        .setAssociationSourceSystemVersion(fhirParams.getFirst("version").orElse(null))
        .setAssociationTargetSystem(fhirParams.getFirst("targetSystem").orElse(null))
        .setAssociationsDecorated(true);
    QueryResult<MapSet> mapSets = mapSetService.query(msParams);

    return mapper.toFhirParameters(mapSets.findFirst().orElse(null));
  }

  public OperationOutcome error(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    OperationOutcomeIssue issue = new OperationOutcomeIssue().setSeverity("error");
    if (fhirParams.getFirst("code").isEmpty()) {
      issue.setCode("required");
      issue.setDetails(new CodeableConcept().setText("No code parameter provided in request"));
    } else if (fhirParams.getFirst("system").isEmpty()) {
      issue.setCode("required");
      issue.setDetails(new CodeableConcept().setText("No system parameter provided in request"));
    } else {
      issue.setCode("not-found");
      issue.setDetails(new CodeableConcept().setText("Translation for code '" + fhirParams.getFirst("code").get() + "' not found"));
    }
    return new OperationOutcome(issue);
  }
}
