package com.kodality.termx.modeler.transformationdefinition.providers;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionQueryParams;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.kodality.termx.modeler.github.CompositeIdUtils.CompositeId;
import static com.kodality.termx.modeler.github.CompositeIdUtils.parseCompositeId;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionUtils.createTransformationDefinitionFromJson;

@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataTransformationDefinitionFhirHandler implements SpaceGithubDataHandler {

  private final TransformationDefinitionService transformationDefinitionService;
  private final ResourceContentTransformationDefinitionFhirProvider resourceContentProvider;

  @Override
  public String getName() {
    return "transformationdefinition-fhir-json";
  }

  @Override
  public String getDefaultDir() {
    return "input/transformation-definitions";
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
    fileContent.forEach((file, jsonContent) -> {
      if (!file.endsWith(".json")) {
        return;
      }

      final CompositeId compositeId = parseCompositeId(StringUtils.removeEnd(file, ".json"));
      final Optional<TransformationDefinition> existingDefinition = transformationDefinitionService.search(new TransformationDefinitionQueryParams()
          .setSpaceId(spaceId)
          .setName(compositeId.code())
          .all()
      ).getData().stream().findFirst();

      existingDefinition.ifPresentOrElse(
          td -> {
            // update existing
            final Map<String, Object> content = JsonUtil.toMap(jsonContent);
            td.getMapping().getReference().setContent(jsonContent);
          },
          () -> {
            // create new
            final TransformationDefinition definition = createTransformationDefinitionFromJson(jsonContent);
//            transformationDefinitionService.save(definition);
          }
      );
    });
  }
}
