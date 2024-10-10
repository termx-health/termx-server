package com.kodality.termx.modeler.transformationdefinition.providers;

import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Singleton
@RequiredArgsConstructor
public class ResourceContentTransformationDefinitionFhirProvider implements ResourceContentProvider {

  private final TransformationDefinitionService transformationDefinitionService;

  @Override
  public String getResourceType() {
    return "TransformationDefinition";
  }

  @Override
  public String getContentType() {
    return "fhir";
  }

  @Override
  public List<ResourceContent> getContent(String id) {
    final TransformationDefinition td = transformationDefinitionService.load(Long.parseLong(id));
    return getContent(td);
  }

  public List<ResourceContent> getContent(TransformationDefinition td) {
    final String content = td.getMapping().getReference().getContent();
    return List.of(new ResourceContent(td.getName() + ".json", content));
  }
}
