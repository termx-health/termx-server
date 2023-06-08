package com.kodality.termserver.fhir.conceptmap.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.terminology.mapset.MapSetService;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapTranslateOperation implements TypeOperationDefinition {
  private final MapSetService mapSetService;

  public String getResourceType() {
    return ResourceType.ConceptMap.name();
  }

  public String getOperationName() {
    return "translate";
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(Parameters req) {
    String code = req.findParameter("code").map(ParametersParameter::getValueString).orElse(null);
    String system = req.findParameter("system").map(ParametersParameter::getValueString).orElse(null);
    String uri = req.findParameter("uri").map(ParametersParameter::getValueString).orElse(null);
    String conceptMapVersion = req.findParameter("conceptMapVersion").map(ParametersParameter::getValueString).orElse(null);
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
    String targetSystem = req.findParameter("targetSystem").map(ParametersParameter::getValueString).orElse(null);

    if (code == null || system == null) {
      return new Parameters();
    }

    MapSetQueryParams msParams = new MapSetQueryParams()
        .setUri(uri)
        .setVersionVersion(conceptMapVersion)
        .setAssociationSourceCode(code)
        .setAssociationSourceSystemUri(system)
        .setAssociationSourceSystemVersion(version)
        .setAssociationTargetSystem(targetSystem)
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
        .addPart(new ParametersParameter("concept").setValueCoding(new Coding().setCode(a.getTarget().getCode()).setSystem(uri)))
    ));
    return p;
  }


}
