package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionUtils;
import com.kodality.termx.terminology.fhir.FhirFshConverter;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.kodality.termx.modeler.structuredefinition.StructureDefinitionUtils.*;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataStructureDefinitionFhirHandler implements SpaceGithubDataHandler {

  private final StructureDefinitionService structureDefinitionService;
  private final ResourceContentStructureDefinitionFhirProvider resourceContentProvider;
  private final Optional<FhirFshConverter> fhirFshConverter;

  @Override
  public String getName() {
    return "structuredefinition-fhir-json";
  }

  @Override
  public String getDefaultDir() {
    return "input/structure-definitions";
  }

  @Override
  public List<ResourceContentProvider.ResourceContent> getContent(Long spaceId) {
    List<StructureDefinition> structureDefinitions = structureDefinitionService.query(new StructureDefinitionQueryParams()
            .setSpaceId(spaceId)
            .setContentFormat("json")
            .all())
        .getData();
    return structureDefinitions.stream().flatMap(cs -> resourceContentProvider.getContent(cs).stream()).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> fileContent) {
    fileContent.forEach((file, jsonContent) -> {
      if (!file.endsWith(".json")) {
        return;
      }

      final StructureDefinitionUtils.CompositeId compositeId = StructureDefinitionUtils.parseCompositeId(StringUtils.removeEnd(file, ".json"));
      final Optional<StructureDefinition> existingDefinition = structureDefinitionService.query(new StructureDefinitionQueryParams()
          .setSpaceId(spaceId)
          .setCode(compositeId.code())
          .setVersion(compositeId.version())
      ).getData().stream().findFirst();

      if (jsonContent == null) {
        existingDefinition
            .filter(sd -> "json".equals(sd.getContentFormat()))
            .flatMap(sd -> structureDefinitionService.load(sd.getId()))
            .ifPresent(sd -> structureDefinitionService.cancel(sd.getId()));
      }

      existingDefinition.ifPresentOrElse(
          sd -> {
            if ("json".equals(sd.getContentFormat())) {
              enrichFromJson(sd, jsonContent);
            } else {
              enrichFromDefinition(sd, createFshStructureDefinitionFromFhir(jsonContent));
            }
            structureDefinitionService.save(sd);
          },
          () -> {
            final StructureDefinition newJsonDefinition = createStructureDefinitionFromJson(jsonContent);
            structureDefinitionService.save(newJsonDefinition);
          }
      );
    });
  }

  @NotNull
  public StructureDefinition createFshStructureDefinitionFromFhir(String json) {
    final StructureDefinition structureDefinition = createStructureDefinitionFromJson(json);
    structureDefinition.setContentFormat("fsh");
    structureDefinition.setContent(toFsh(json));
    return structureDefinition;
  }

  private String toFsh(String json) {
    return fhirFshConverter.orElseThrow(() -> new RuntimeException("FhirFshConverter is not initialized")).toFsh(json).join();
  }

}
