package com.kodality.termx.fhir.conceptmap.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.fhir.conceptmap.ConceptMapFhirMapper;
import com.kodality.termx.terminology.mapset.MapSetService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapTranslateOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final MapSetService mapSetService;

  public String getResourceType() {
    return ResourceType.ConceptMap.name();
  }

  public String getOperationName() {
    return "translate";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = ConceptMapFhirMapper.parseCompositeId(id.getResourceId());
    String cmId = parts[0];
    String versionNumber = parts[1];
    Parameters resp = run(cmId, null, versionNumber, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    String url = req.findParameter("url").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "url parameter required"));
    String conceptMapVersion = req.findParameter("conceptMapVersion").map(ParametersParameter::getValueString).orElse(null);
    Parameters resp = run(null, url, conceptMapVersion, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(String cmId, String cmUrl, String cmVersion, Parameters req) {
    String code = req.findParameter("code").or(() -> req.findParameter("sourceCode")).map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "sourceCode (code) parameter required"));
    String system = req.findParameter("system").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "system parameter required"));
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
    String targetSystem = req.findParameter("targetSystem").map(ParametersParameter::getValueString).orElse(null);

    MapSetQueryParams msParams = new MapSetQueryParams()
        .setId(cmId)
        .setUri(cmUrl)
        .setVersionVersion(cmVersion)
        .setAssociationSourceCode(code)
        .setAssociationSourceSystemUri(system)
        .setAssociationSourceSystemVersion(version)
        .setAssociationTargetSystemUri(targetSystem)
        .setAssociationsDecorated(true);
    msParams.setLimit(1);
    MapSet mapSet = mapSetService.query(msParams).findFirst().orElse(null);
    if (mapSet == null || mapSet.getAssociations() == null) {
      return new Parameters().addParameter(new ParametersParameter("result").setValueBoolean(false));
    }
    Parameters p = new Parameters();
    p.addParameter(new ParametersParameter("result").setValueBoolean(true));
    mapSet.getAssociations().forEach(a -> p.addParameter(new ParametersParameter("match")
        .addPart(new ParametersParameter("equivalence").setValueCode(a.getAssociationType()))
        .addPart(new ParametersParameter("concept").setValueCoding(new Coding().setCode(a.getTarget().getCode()).setSystem(cmUrl)))
    ));
    return p;
  }


}
