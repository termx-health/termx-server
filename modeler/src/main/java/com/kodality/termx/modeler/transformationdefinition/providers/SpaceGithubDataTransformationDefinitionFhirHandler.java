package com.kodality.termx.modeler.transformationdefinition.providers;

import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResourceReference;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionQueryParams;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionService;
import com.kodality.termx.modeler.transformationdefinition.TransformerService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.StructureMap.StructureMapStructureComponent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.kodality.termx.modeler.github.CompositeIdUtils.CompositeId;
import static com.kodality.termx.modeler.github.CompositeIdUtils.parseCompositeId;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceSource.statik;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionUtils.createTransformationDefinitionFromResource;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataTransformationDefinitionFhirHandler implements SpaceGithubDataHandler {

  private final TransformationDefinitionService transformationDefinitionService;
  private final ResourceContentTransformationDefinitionFhirProvider resourceContentProvider;
  private final TransformerService transformerService;

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
            if (statik.equals(td.getMapping().getSource())) {
              updateTransferDefinitionFromJson(td, jsonContent);
              td.getMapping().getReference().setContent(jsonContent);
            }
            // test_source - leave it as it is, we can generate it
            // fhir_resource will be updated during the save call
            transformationDefinitionService.save(td);
          },
          () -> {
            // test_source (can be null)
            final TransformationDefinition definition = createTransformationDefinitionFromJson(jsonContent);
            definition.setName(compositeId.code());
            transformationDefinitionService.save(definition);
          }
      );
    });
  }

  private void updateTransferDefinitionFromJson(TransformationDefinition td, String sourceContent) {
    final StructureMap structureMap = transformerService.parse(sourceContent);

    // ! we don't have concept maps here
    // TODO: update concept maps

    updateResources(td, structureMap.getStructure());
    addNewResources(td, structureMap.getStructure());
    removeRedundant(td, structureMap.getStructure());
  }

  private TransformationDefinition createTransformationDefinitionFromJson(String json) {
    final StructureMap structureMap = transformerService.parse(json);
    try {
      return createTransformationDefinitionFromResource(structureMap);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  private static void updateResources(TransformationDefinition td, List<StructureMapStructureComponent> structureComponents) {
    final Map<String, StructureMapStructureComponent> source = structureComponents.stream()
        .filter(sc -> td.getResources().stream().anyMatch(r -> r.getName().equals(sc.getAlias())))
        .collect(toMap(StructureMapStructureComponent::getAlias, Function.identity()));
    td.getResources().forEach(r -> {
      if (source.containsKey(r.getName())
          && r.getSource().equals("url")
          && r.getReference() == null) {
        r.setReference(
            new TransformationDefinitionResourceReference()
                .setResourceUrl(source.get(r.getName()).getUrl())
        );
      }
    });
  }

  private static void removeRedundant(TransformationDefinition td, List<StructureMapStructureComponent> structureComponents) {
    final Predicate<TransformationDefinitionResource> isContainedInSourcePredicate =
        r -> structureComponents.stream()
            .anyMatch(sc -> sc.getAlias().equals(r.getName()));
    td.setResources(td.getResources().stream()
        .filter(isContainedInSourcePredicate)
        .toList());
  }

  private static void addNewResources(TransformationDefinition td, List<StructureMapStructureComponent> structureComponents) {
    final List<StructureMapStructureComponent> newComponents = structureComponents.stream()
        .filter(sc -> td.getResources().stream()
            .noneMatch(r -> r.getName().equals(sc.getAlias()))
        )
        .toList();
    newComponents.forEach(item ->
        td.getResources().add(
            new TransformationDefinitionResource()
                .setName(item.getAlias())
                .setSource("url")
                .setType("definition")
                .setReference(
                    new TransformationDefinitionResourceReference()
                        .setResourceUrl(item.getUrl())
                )
        ));
  }
}
