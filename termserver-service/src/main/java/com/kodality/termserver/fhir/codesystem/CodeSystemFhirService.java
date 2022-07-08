package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.time.LocalDateTime;
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
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

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
        .setVersionReleaseDateGe(fhirParams.getFirst("date").map(d -> LocalDateTime.parse(d).toLocalDate()).orElse(null))
        .setVersionExpirationDateLe(fhirParams.getFirst("date").map(d -> LocalDateTime.parse(d).toLocalDate()).orElse(null))
        .setVersionsDecorated(true).setConceptsDecorated(true).setPropertiesDecorated(true);
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

  public com.kodality.zmei.fhir.resource.terminology.CodeSystem get(Long codeSystemVersionId) {
    CodeSystem codeSystem = codeSystemService.query(new CodeSystemQueryParams()
        .setVersionId(codeSystemVersionId)
        .setPropertiesDecorated(true)
    ).findFirst().orElse(null);
    if (codeSystem == null) {
      return null;
    }
    CodeSystemVersion version = codeSystemVersionService.load(codeSystemVersionId);
    CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams().setCodeSystemVersionId(version.getId());
    codeSystemEntityVersionParams.all();
    version.setEntities(codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData());
    return mapper.toFhir(codeSystem, version);

  }
}
