package com.kodality.termx.modeler.transformationdefinition.providers;

import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionQueryParams;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataTransformationDefinitionFmlHandler implements SpaceGithubDataHandler {

  private final TransformationDefinitionService transformationDefinitionService;
  private final ResourceContentTransformationDefinitionFmlProvider resourceContentProvider;

  @Override
  public String getName() {
    return "transformationdefinition-fhir-fml";
  }

  @Override
  public String getDefaultDir() {
    return "input/transformation-definitions/fml";
  }

  @Override
  public List<ResourceContent> getContent(Long spaceId) {
    final List<TransformationDefinition> result = transformationDefinitionService.search(
            new TransformationDefinitionQueryParams()
                .setSpaceId(spaceId)
                .all())
        .getData();
    return result.stream().flatMap(td -> resourceContentProvider.getContent(td).stream()).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> fileContent) {
    // do nothing
  }
}
