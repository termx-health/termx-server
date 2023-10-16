package com.kodality.termx.terminology.fhir.conceptmap.operations;

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
    return null;
  }

}
