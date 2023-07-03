package com.kodality.termx.fhir.conceptmap.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapClosureOperation implements TypeOperationDefinition {

  public String getResourceType() {
    return ResourceType.ConceptMap.name();
  }

  public String getOperationName() {
    return "closure";
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    ConceptMap resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private ConceptMap run(Parameters req) {
//    String name = req.findParameter("name").map(ParametersParameter::getValueString)
//        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "name parameter required"));
//    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
//    List<Coding> concepts = req.getParameter().stream().filter(p -> p.getName().equals("concept")).map(ParametersParameter::getValueCoding).toList();
//

    return null;
  }

}
