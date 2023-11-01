package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.terminology.codesystem.compare.CodeSystemCompareService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
@RequiredArgsConstructor
public class CodeSystemCompareOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemCompareService compareService;
  @Override
  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  @Override
  public String getOperationName() {
    return "compare";
  }

  @Override
  public ResourceContent run(ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String system = req.findParameter("system").map(ParametersParameter::getValueString).orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "system required"));
    String versionA = req.findParameter("versionA").map(ParametersParameter::getValueString).orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "versionA required"));
    String versionB = req.findParameter("versionB").map(ParametersParameter::getValueString).orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "versionB required"));

    Long versionAId = codeSystemVersionService.loadVersionByUri(system, versionA).map(CodeSystemVersionReference::getId).orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "versionA not found"));
    Long versionBId = codeSystemVersionService.loadVersionByUri(system, versionB).map(CodeSystemVersionReference::getId).orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "versionB not found"));
    return new ResourceContent(FhirMapper.toJson(CodeSystemFhirMapper.toFhir(compareService.compare(versionAId, versionBId))), "json");
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    String[] parts = CodeSystemFhirMapper.parseCompositeId(id.getResourceId());
    String csId = parts[0];

    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String versionA = req.findParameter("versionA").map(ParametersParameter::getValueString).orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "versionA required"));
    String versionB = req.findParameter("versionB").map(ParametersParameter::getValueString).orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "versionB required"));

    Long versionAId = codeSystemVersionService.load(csId, versionA).map(CodeSystemVersionReference::getId).orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "versionA not found"));
    Long versionBId = codeSystemVersionService.load(csId, versionB).map(CodeSystemVersionReference::getId).orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "versionB not found"));
    return new ResourceContent(FhirMapper.toJson(CodeSystemFhirMapper.toFhir(compareService.compare(versionAId, versionBId))), "json");
  }
}
