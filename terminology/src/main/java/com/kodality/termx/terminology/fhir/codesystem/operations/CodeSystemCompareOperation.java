package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.terminology.codesystem.compare.CodeSystemCompareResult;
import com.kodality.termx.terminology.terminology.codesystem.compare.CodeSystemCompareResult.CodeSystemCompareResultDiffItem;
import com.kodality.termx.terminology.terminology.codesystem.compare.CodeSystemCompareService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
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

    Long versionAId = codeSystemVersionService.loadVersionByUri(system, versionA).map(CodeSystemVersionReference::getId).orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "versionA not found"));
    Long versionBId = codeSystemVersionService.loadVersionByUri(system, versionB).map(CodeSystemVersionReference::getId).orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "versionB not found"));
    return new ResourceContent(FhirMapper.toJson(toFhir(compareService.compare(versionAId, versionBId))), "json");
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    String[] parts = CodeSystemFhirMapper.parseCompositeId(id.getResourceId());
    String csId = parts[0];

    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String versionA = req.findParameter("versionA").map(ParametersParameter::getValueString).orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "versionA required"));
    String versionB = req.findParameter("versionB").map(ParametersParameter::getValueString).orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "versionB required"));

    Long versionAId = codeSystemVersionService.load(csId, versionA).map(CodeSystemVersionReference::getId).orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "versionA not found"));
    Long versionBId = codeSystemVersionService.load(csId, versionB).map(CodeSystemVersionReference::getId).orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "versionB not found"));
    return new ResourceContent(FhirMapper.toJson(toFhir(compareService.compare(versionAId, versionBId))), "json");
  }

  private static Parameters toFhir(CodeSystemCompareResult result) {
    Parameters parameters = new Parameters();
    if (CollectionUtils.isNotEmpty(result.getAdded())) {
      ParametersParameter p = new ParametersParameter("added");
      result.getAdded().forEach(a -> p.addPart(new ParametersParameter("code").setValueCode(a)));
      parameters.addParameter(p);
    }
    if (CollectionUtils.isNotEmpty(result.getDeleted())) {
      ParametersParameter p = new ParametersParameter("deleted");
      result.getDeleted().forEach(d -> p.addPart(new ParametersParameter("code").setValueCode(d)));
      parameters.addParameter(p);
    }
    if (CollectionUtils.isNotEmpty(result.getChanged())) {
      ParametersParameter p = new ParametersParameter("changed");
      result.getChanged().forEach(c -> {
        ParametersParameter code = new ParametersParameter("code").setValueCode(c.getCode());
        if (c.getDiff() != null && c.getDiff().getMew() != null) {
          code.addPart(toFhir(c.getDiff().getMew(), "new"));
        }
        if (c.getDiff() != null && c.getDiff().getOld() != null) {
          code.addPart(toFhir(c.getDiff().getOld(), "old"));
        }
        p.addPart(code);
      });
      parameters.addParameter(p);
    }
    return parameters;
  }

  private static ParametersParameter toFhir(CodeSystemCompareResultDiffItem diff, String name) {
    ParametersParameter pp = new ParametersParameter(name);
    if (diff.getStatus() != null) {
      pp.addPart(new ParametersParameter("status").setValueCode(diff.getStatus()));
    }
    if (diff.getDescription() != null) {
      pp.addPart(new ParametersParameter("description").setValueString(diff.getDescription()));
    }
    if (diff.getDesignations() != null) {
      diff.getDesignations().forEach(d -> pp.addPart(toFhir(d, "designation")));
    }
    if (diff.getProperties() != null) {
      diff.getProperties().forEach(p -> pp.addPart(toFhir(p, "property")));
    }
    return pp;
  }

  private static ParametersParameter toFhir(String property, String name) {
    ParametersParameter pp = new ParametersParameter(name);
    String[] props = property.split("\\|");
    if (props.length == 3) {
      pp.addPart(new ParametersParameter("property").setValueString(props[0]));
      pp.addPart(new ParametersParameter("language").setValueString(props[1]));
      pp.setValueString(props[2]);
    }
    if (props.length == 2) {
      pp.addPart(new ParametersParameter("property").setValueString(props[0]));
      pp.setValueString(props[1]);
    }
    return pp;
  }
}
