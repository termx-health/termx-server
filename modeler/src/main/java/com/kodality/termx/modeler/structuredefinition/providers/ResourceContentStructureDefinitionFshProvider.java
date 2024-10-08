package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@RequiredArgsConstructor
public class ResourceContentStructureDefinitionFshProvider implements ResourceContentProvider {

  private final StructureDefinitionService structureDefinitionService;


  @Override
  public String getResourceType() {
    return "StructureDefinition";
  }

  @Override
  public String getContentType() {
    return "fsh";
  }

  @Override
  public List<ResourceContentProvider.ResourceContent> getContent(String id) {
    final StructureDefinition sd = structureDefinitionService.load(Long.parseLong(id))
        .orElseThrow(() -> new NotFoundException("StructureDefinition not found: " + id));
    return getContent(sd);
  }

  public List<ResourceContentProvider.ResourceContent> getContent(StructureDefinition sd) {
    String fhirId = getFhirId(sd);
    return List.of(new ResourceContentProvider.ResourceContent(fhirId + ".fsh", sd.getContent()));
  }

  @NotNull
  private static String getFhirId(StructureDefinition sd) {
    return Stream.of(sd.getCode(), sd.getVersion())
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining(BaseFhirMapper.SEPARATOR));
  }
}
