package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemFhirService {
  private final CodeSystemFhirMapper mapper;
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;

  public Parameters lookup(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("code").isEmpty() || fhirParams.getFirst("system").isEmpty()) {
      return new Parameters();
    }

    CodeSystemQueryParams csParams = new CodeSystemQueryParams()
        .setConceptCode(fhirParams.getFirst("code").orElse(null))
        .setConceptCodeSystemVersion(fhirParams.getFirst("version").orElse(null))
        .setUri(fhirParams.getFirst("system").orElse(null))
        .setVersionVersion(fhirParams.getFirst("version").orElse(null))
        .setVersionReleaseDateGe(fhirParams.getFirst("date").map(LocalDate::parse).orElse(null))
        .setVersionExpirationDateLe(fhirParams.getFirst("date").map(LocalDate::parse).orElse(null))
        .setVersionsDecorated(true).setConceptsDecorated(true).setPropertiesDecorated(true);
    csParams.setOffset(fhirParams.getOffset());
    csParams.setLimit(fhirParams.getCount());
    csParams.setSort(fhirParams.getSort());
    QueryResult<CodeSystem> codeSystems = codeSystemService.query(csParams);

    return mapper.toFhirParameters(codeSystems.findFirst().orElse(null), fhirParams);
  }

  public Parameters validateCode(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("code").isEmpty() || fhirParams.getFirst("system").isEmpty()) {
      return new Parameters();
    }

    ConceptQueryParams cParams = new ConceptQueryParams()
        .setCode(fhirParams.getFirst("code").orElse(null))
        .setCodeSystemVersion(fhirParams.getFirst("version").orElse(null))
        .setCodeSystemUri(fhirParams.getFirst("system").orElse(null));
    cParams.setOffset(fhirParams.getOffset());
    cParams.setLimit(fhirParams.getCount());
    cParams.setSort(fhirParams.getSort());
    QueryResult<Concept> concepts = conceptService.query(cParams);

    return mapper.toFhirParameters(concepts.findFirst().orElse(null), fhirParams);
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
      issue.setDetails(new CodeableConcept().setText("Code '" + fhirParams.getFirst("code").get() + "' not found"));
    }
    return new OperationOutcome(issue);
  }
}
