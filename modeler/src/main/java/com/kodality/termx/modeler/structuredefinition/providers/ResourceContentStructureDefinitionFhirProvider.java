package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

import static com.kodality.termx.modeler.github.CompositeIdUtils.getFhirId;

@Slf4j
@Singleton
public class ResourceContentStructureDefinitionFhirProvider extends ResourceContentStructureDefinitionBaseProvider {

  public ResourceContentStructureDefinitionFhirProvider(StructureDefinitionService structureDefinitionService) {
    super(structureDefinitionService);
  }

  @Override
  public String getResourceType() {
    return "StructureDefinition";
  }

  @Override
  public String getContentType() {
    return "fhir";
  }

  public List<ResourceContent> getContent(String id, String version) {
    StructureDefinition sd = getStructureDefinition(id, version);
    if (!"json".equals(sd.getContentFormat())) {
      return Collections.emptyList();
    }
    return getContent(sd);
  }

  public List<ResourceContent> getContent(StructureDefinition sd) {
    String fhirId = getFhirId(sd.getCode(), sd.getVersion());
    final String json = JsonUtil.toPrettyJson(JsonUtil.toMap(sd.getContent()));
    return List.of(new ResourceContent(fhirId + ".json", json));
  }
}
