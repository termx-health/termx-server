package com.kodality.termserver.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemSubsumesOperation implements TypeOperationDefinition {
  private final ConceptService conceptService;
  private final CodeSystemVersionService codeSystemVersionService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "subsumes";
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public Parameters run(Parameters req) {
    String system = req.findParameter("system").map(ParametersParameter::getValueString).orElse(null);
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
    String codeA = req.findParameter("codeA").map(ParametersParameter::getValueString).orElse(null);
    String codeB = req.findParameter("codeA").map(ParametersParameter::getValueString).orElse(null);
    Coding codingA = req.findParameter("codingA").map(ParametersParameter::getValueCoding).orElse(null);
    Coding codingB = req.findParameter("codingB").map(ParametersParameter::getValueCoding).orElse(null);

    if (codeA == null && codingA == null || codeB == null && codingB == null) {
      throw new FhirException(400, IssueType.INVALID, "either code or coding required");
    }
    if ((codeA != null || codeB != null) && system == null) {
      throw new FhirException(400, IssueType.INVALID, "system is required");
    }

    Concept conceptA = codingA != null ? findConcept(codingA) : findConcept(system, version, codeA);
    Concept conceptB = codingB != null ? findConcept(codingB) : findConcept(system, version, codeB);
    return subsumes(conceptA, conceptB);
  }

  private Parameters subsumes(Concept conceptA, Concept conceptB) {
    List<Long> codeAProperties = conceptA.getVersions().stream()
        .flatMap(entityVersion -> entityVersion.getPropertyValues().stream())
        .map(EntityPropertyValue::getEntityPropertyId).toList();
    List<Long> codeBProperties = conceptB.getVersions().stream()
        .flatMap(entityVersion -> entityVersion.getPropertyValues().stream())
        .map(EntityPropertyValue::getEntityPropertyId).toList();

    boolean subsumes = codeAProperties.stream().allMatch(ap -> codeBProperties.stream().anyMatch(bp -> bp.equals(ap)));
    boolean subsumedBy = codeBProperties.stream().allMatch(bp -> codeAProperties.stream().anyMatch(ap -> ap.equals(bp)));

    return new Parameters().addParameter(
        new ParametersParameter()
            .setName("outcome")
            .setValueCode(subsumes && subsumedBy ? "equivalent" : subsumes ? "subsumes" : subsumedBy ? "subsumed-by" : "not-subsumed")
    );
  }

  private Concept findConcept(Coding coding) {
    String version = coding.getVersion() != null ? coding.getVersion() : codeSystemVersionService.loadLastVersionByUri(coding.getSystem()).getVersion();
    return findConcept(coding.getSystem(), version, coding.getCode());
  }

  private Concept findConcept(String uri, String version, String code) {
    version = version != null ? version : codeSystemVersionService.loadLastVersionByUri(uri).getVersion();
    ConceptQueryParams conceptParams = new ConceptQueryParams();
    conceptParams.setCode(code);
    conceptParams.setCodeSystemUri(uri);
    conceptParams.setCodeSystemVersion(version);
    conceptParams.setLimit(1);
    return conceptService.query(conceptParams).findFirst()
        .orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "Concept '" + code + "' not found in " + uri));
  }

}
