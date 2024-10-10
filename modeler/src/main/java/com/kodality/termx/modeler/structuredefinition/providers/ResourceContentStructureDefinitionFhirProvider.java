package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.kodality.termx.modeler.github.CompositeIdUtils.getFhirId;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ResourceContentStructureDefinitionFhirProvider implements ResourceContentProvider {

  private final StructureDefinitionService structureDefinitionService;


  @Override
  public String getResourceType() {
    return "StructureDefinition";
  }

  @Override
  public String getContentType() {
    return "fhir";
  }

  @Override
  public List<ResourceContent> getContent(String id) {
    final StructureDefinition sd = structureDefinitionService.load(Long.parseLong(id))
        .orElseThrow(() -> new NotFoundException("StructureDefinition not found: " + id));
    return getContent(sd);
  }

  public List<ResourceContent> getContent(StructureDefinition sd) {
    String fhirId = getFhirId(sd.getCode(), sd.getVersion());
    final String json = JsonUtil.toPrettyJson(JsonUtil.toMap(sd.getContent()));
    return List.of(new ResourceContent(fhirId + ".json", json));
  }
}
