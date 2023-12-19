package com.kodality.termx.modeler.fhir;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionQueryParams;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionService;
import com.kodality.termx.modeler.transformationdefinition.TransformationResult;
import com.kodality.termx.modeler.transformationdefinition.TransformerService;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Binary;
import com.kodality.zmei.fhir.resource.other.Parameters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
@RequiredArgsConstructor
public class StructureMapTransformOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final TransformationDefinitionService transformationDefinitionService;
  private final TransformerService transformerService;

  @Override
  public String getResourceType() {
    return ResourceType.StructureMap.name();
  }

  @Override
  public String getOperationName() {
    return "transform";
  }


  @Override
  public ResourceContent run(ResourceId id, ResourceContent resourceContent) {
    Parameters p = FhirMapper.fromJson(resourceContent.getValue(), Parameters.class);
    String input = p.findParameter("content").map(pp -> ((Binary) pp.getResource()).getDataString())
        .orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "content required"));

    var params = new TransformationDefinitionQueryParams();
    params.setFhirIds(id.getResourceId());
    params.limit(2);
    QueryResult<TransformationDefinition> resp = transformationDefinitionService.search(params);
    if (resp.getData().size() > 1) {
      throw new FhirException(400, IssueType.INVALID, "Matched multiple StructureMap resources");
    }

    TransformationResult transformation = transformerService.transform(input, resp.findFirst().orElseThrow());
    return new ResourceContent(transformation.getResult(), transformation.getResult().startsWith("<") ? "xml" : "json");
  }

  @Override
  public ResourceContent run(ResourceContent resourceContent) {
    Parameters p = FhirMapper.fromJson(resourceContent.getValue(), Parameters.class);
    String input = p.findParameter("content").map(pp -> ((Binary) pp.getResource()).getDataString())
        .orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "content required"));

    // canonical URL
    String source = p.findParameter("valueUri").map(c -> c.getValueUri() == null ? c.getValueString() : c.getValueUri()).orElse(null);

    TransformationDefinition internalDefinition = null;
     if (source != null) {
      var params = new TransformationDefinitionQueryParams();
      params.setFhirUrls(source); // NB! can match multiple definitions, but using the first one
      params.limit(2);
      QueryResult<TransformationDefinition> resp = transformationDefinitionService.search(params);
      if (resp.getData().size() > 1) {
        throw new FhirException(400, IssueType.INVALID, "Matched multiple StructureMap resources");
      }
      internalDefinition = resp.findFirst().orElseThrow();
    }

    if (internalDefinition == null) {
      throw new FhirException(400, IssueType.NOTFOUND, "StructureMap is missing");
    }

    TransformationResult transformation = transformerService.transform(input, internalDefinition);
    return new ResourceContent(transformation.getResult(), transformation.getResult().startsWith("<") ? "xml" : "json");
  }

  public Optional<Map<String, Object>> findParameter(List<Map<String, Object>> parameters, String name) {
    return parameters == null ? Optional.empty() : parameters.stream().filter(pp -> name.equals(pp.get("name"))).findFirst();
  }
}
