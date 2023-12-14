package com.kodality.termx.modeler.fhir;

import com.kodality.commons.util.JsonUtil;
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
    // @see com.kodality.zmei.fhir.resource.other.Parameters
    List<Map<String, Object>> parameters = (List<Map<String, Object>>) JsonUtil.toMap(resourceContent.getValue()).get("parameter");

    Object instance = findParameter(parameters, "content").map(p -> p.get("resource"))
        .orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "content required"));

    var params = new TransformationDefinitionQueryParams();
    params.setFhirIds(id.getResourceId());
    params.limit(1);
    TransformationDefinition internalDefinition = transformationDefinitionService.search(params).findFirst().orElseThrow();

    TransformationResult transformation = transformerService.transform(FhirMapper.toJson(instance), internalDefinition);
    return new ResourceContent(transformation.getResult(), "json");
  }

  @Override
  public ResourceContent run(ResourceContent resourceContent) {
    // @see com.kodality.zmei.fhir.resource.other.Parameters
    List<Map<String, Object>> parameters = (List<Map<String, Object>>) JsonUtil.toMap(resourceContent.getValue()).get("parameter");

    Object instance = findParameter(parameters, "content").map(p -> p.get("resource"))
        .orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "content required"));

    // canonical URL
    String source = (String) findParameter(parameters, "source").map(p -> p.getOrDefault("valueUri", p.get("valueString"))).orElse(null);
    // StructureMap as FHIR resource
    Object sourceMap = findParameter(parameters, "sourceMap").map(p -> p.get("valueString")).orElse(null);
    // StructureMaps as FML
    List<String> srcMaps = parameters.stream().filter(p -> "srcMap".equals(p.get("name"))).map(p -> (String) p.get("valueString")).toList();

    TransformationDefinition internalDefinition = null;
    if (!srcMaps.isEmpty()) {
      throw new FhirException(400, IssueType.NOTSUPPORTED, "srcMap is not supported!");
    } else if (sourceMap != null) {
      throw new FhirException(400, IssueType.NOTSUPPORTED, "sourceMap is not supported!");
    } else if (source != null) {
      var params = new TransformationDefinitionQueryParams();
      params.setFhirUrls(source); // NB! can match multiple definitions, but using the first one
      params.limit(1);
      internalDefinition = transformationDefinitionService.search(params).findFirst().orElseThrow();
    }

    if (internalDefinition == null) {
      throw new FhirException(400, IssueType.NOTFOUND, "StructureMap is missing");
    }

    TransformationResult transformation = transformerService.transform(FhirMapper.toJson(instance), internalDefinition);
    return new ResourceContent(transformation.getResult(), "json");
  }

  public Optional<Map<String, Object>> findParameter(List<Map<String, Object>> parameters, String name) {
    return parameters == null ? Optional.empty() : parameters.stream().filter(pp -> name.equals(pp.get("name"))).findFirst();
  }
}
