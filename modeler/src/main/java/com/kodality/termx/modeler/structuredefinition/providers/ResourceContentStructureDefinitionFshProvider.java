package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.kodality.termx.modeler.github.CompositeIdUtils.getFhirId;

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
    String fhirId = getFhirId(sd.getCode(), sd.getVersion());
    return List.of(new ResourceContentProvider.ResourceContent(fhirId + ".fsh", sd.getContent()));
  }
}
