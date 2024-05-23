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
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.resource.other.Parameters;
import java.util.List;
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

    return buildResponse(transformerService.transform(input, resp.findFirst().orElseThrow()));
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
      throw new FhirException(404, IssueType.NOTFOUND, "StructureMap is missing");
    }

    return buildResponse(transformerService.transform(input, internalDefinition));
  }


  private static ResourceContent buildResponse(TransformationResult transformation) {
    if (transformation.getError() != null) {
      OperationOutcome oo = new OperationOutcome();
      oo.setIssue(List.of(new OperationOutcomeIssue().setSeverity("error").setCode(transformation.getError())));
      return new ResourceContent(FhirMapper.toJson(oo), "json");
    }
    return new ResourceContent(transformation.getResult(), transformation.getResult().startsWith("<") ? "xml" : "json");
  }
}
