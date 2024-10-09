package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.core.sys.space.SpaceGithubDataHandler;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionUtils;
import com.kodality.termx.terminology.fhir.FhirFshConverter;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.kodality.termx.modeler.structuredefinition.StructureDefinitionUtils.*;

@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataStructureDefinitionFshHandler implements SpaceGithubDataHandler {

  private final StructureDefinitionService structureDefinitionService;
  private final ResourceContentStructureDefinitionFshProvider resourceContentProvider;
  private final Optional<FhirFshConverter> fhirFshConverter;

  @Override
  public String getName() {
    return "structuredefinition-fhir-fsh";
  }

  @Override
  public String getDefaultDir() {
    return "input/fsh/structure-definitions";
  }

  @Override
  public List<ResourceContentProvider.ResourceContent> getContent(Long spaceId) {
    List<StructureDefinition> structureDefinitions = structureDefinitionService.query(new StructureDefinitionQueryParams()
            .setSpaceId(spaceId)
            .setContentFormat("fsh")
            .all())
        .getData();
    return structureDefinitions.stream().flatMap(cs -> resourceContentProvider.getContent(cs).stream()).toList();
  }

  @Override
  public void saveContent(Long spaceId, Map<String, String> fileContent) {
    fileContent.forEach((file, fshContent) -> {
      if (!file.endsWith(".fsh")) {
        return;
      }

      final StructureDefinitionUtils.CompositeId compositeId = parseCompositeId(StringUtils.removeEnd(file, ".fsh"));
      final Optional<StructureDefinition> existingDefinition = structureDefinitionService.query(new StructureDefinitionQueryParams()
          .setSpaceId(spaceId)
          .setCode(compositeId.code())
          .setVersion(compositeId.version())
      ).getData().stream().findFirst();

      if (fshContent == null) {
        existingDefinition
            .filter(sd -> "fsh".equals(sd.getContentFormat()))
            .flatMap(sd -> structureDefinitionService.load(sd.getId()))
            .ifPresent(sd -> structureDefinitionService.cancel(sd.getId()));
      }

      existingDefinition.ifPresentOrElse(
          sd -> {
            if ("fsh".equals(sd.getContentFormat())) {
              enrichFromDefinition(sd, createFshStructureDefinition(fshContent));
            } else {
              enrichFromJson(sd, toFhir(fshContent));
            }
            structureDefinitionService.save(sd);
          },
          () -> {
            final StructureDefinition newDefinition = createFshStructureDefinition(fshContent);
            structureDefinitionService.save(newDefinition);
          }
      );
    });
  }

  @NotNull
  private StructureDefinition createFshStructureDefinition(String fshContent) {
    final StructureDefinition structureDefinition = createFhirStructureDefinitionFromFsh(fshContent);
    structureDefinition.setContent(fshContent);
    structureDefinition.setContentFormat("fsh");
    return structureDefinition;
  }

  private StructureDefinition createFhirStructureDefinitionFromFsh(String fshContent) {
    final String json = toFhir(fshContent);
    return createStructureDefinitionFromJson(json);
  }

  private String toFhir(String fshContent) {
    return fhirFshConverter.orElseThrow(() -> new RuntimeException("FhirFshConverter is not initialized")).toFhir(fshContent).join();
  }
}
