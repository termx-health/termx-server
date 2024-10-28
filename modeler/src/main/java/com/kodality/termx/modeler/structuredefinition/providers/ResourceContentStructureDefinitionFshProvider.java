package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

import static com.kodality.termx.modeler.github.CompositeIdUtils.getFhirId;

@Singleton
public class ResourceContentStructureDefinitionFshProvider extends ResourceContentStructureDefinitionBaseProvider {

  public ResourceContentStructureDefinitionFshProvider(StructureDefinitionService structureDefinitionService) {
    super(structureDefinitionService);
  }

  @Override
  public String getResourceType() {
    return "StructureDefinition";
  }

  @Override
  public String getContentType() {
    return "fsh";
  }

  public List<ResourceContentProvider.ResourceContent> getContent(String id, String version) {
    StructureDefinition sd = getStructureDefinition(id, version);
    if (!"fsh".equals(sd.getContentFormat())) {
      return Collections.emptyList();
    }
    return getContent(sd);
  }

  public List<ResourceContentProvider.ResourceContent> getContent(StructureDefinition sd) {
    String fhirId = getFhirId(sd.getCode(), sd.getVersion());
    return List.of(new ResourceContentProvider.ResourceContent(fhirId + ".fsh", sd.getContent()));
  }
}
