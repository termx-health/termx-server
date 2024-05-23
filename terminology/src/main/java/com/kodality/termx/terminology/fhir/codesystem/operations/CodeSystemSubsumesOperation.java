package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemSubsumesOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final ConceptService conceptService;
  private final CodeSystemVersionService codeSystemVersionService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "subsumes";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    String[] parts = CodeSystemFhirMapper.parseCompositeId(id.getResourceId());
    String csId = parts[0];
    String versionNumber = parts[1];
    CodeSystemVersion csv = codeSystemVersionService.load(csId, versionNumber)
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept version not found"));
    Parameters resp = run(csv, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public Parameters run(Parameters req) {
    String system = req.findParameter("system").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code parameter required"));
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
    CodeSystemVersion csv = version == null ? codeSystemVersionService.loadLastVersionByUri(system) : codeSystemVersionService.loadVersionByUri(system, version)
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept version not found"));
    return run(csv, req);
  }

  public Parameters run(CodeSystemVersion csv, Parameters req) {
    String codeA = req.findParameter("codeA").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "codeA parameter required"));
    String codeB = req.findParameter("codeB").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "codeB parameter required"));
    Concept conceptA = findConcept(csv, codeA);
    Concept conceptB = findConcept(csv, codeB);
    return subsumes(conceptA, conceptB);
  }

  private Parameters subsumes(Concept conceptA, Concept conceptB) {
    List<Long> codeAProperties = conceptA.getVersions().stream()
        .flatMap(entityVersion -> entityVersion.getPropertyValues() == null ? Stream.empty() : entityVersion.getPropertyValues().stream())
        .map(EntityPropertyValue::getEntityPropertyId).toList();
    List<Long> codeBProperties = conceptB.getVersions().stream()
        .flatMap(entityVersion -> entityVersion.getPropertyValues() == null ? Stream.empty() : entityVersion.getPropertyValues().stream())
        .map(EntityPropertyValue::getEntityPropertyId).toList();

    boolean subsumes = codeAProperties.stream().allMatch(ap -> codeBProperties.stream().anyMatch(bp -> bp.equals(ap)));
    boolean subsumedBy = codeBProperties.stream().allMatch(bp -> codeAProperties.stream().anyMatch(ap -> ap.equals(bp)));

    return new Parameters().addParameter(
        new ParametersParameter()
            .setName("outcome")
            .setValueCode(subsumes && subsumedBy ? "equivalent" : subsumes ? "subsumes" : subsumedBy ? "subsumed-by" : "not-subsumed")
    );
  }

  private Concept findConcept(CodeSystemVersion codeSystemVersion, String code) {
    ConceptQueryParams conceptParams = new ConceptQueryParams();
    conceptParams.setCode(code);
    conceptParams.setCodeSystemVersionId(codeSystemVersion.getId());
    conceptParams.setLimit(1);
    conceptParams.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));
    return conceptService.query(conceptParams).findFirst()
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept '" + code + "' not found in " + codeSystemVersion.getCodeSystem()));
  }

}
