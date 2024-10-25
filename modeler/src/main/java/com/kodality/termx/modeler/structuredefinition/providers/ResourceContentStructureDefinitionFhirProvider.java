package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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
  public List<ResourceContent> getContent(String idVersionPipe) {
    String[] pipe = PipeUtil.parsePipe(idVersionPipe);
    return getContent(pipe[0], prepareVersion(pipe[1]));
  }

  public List<ResourceContent> getContent(String id, String version) {
    StructureDefinition sd;
    if (StringUtils.isNumeric(id)) {
      sd = structureDefinitionService.load(Long.parseLong(id))
          .orElseThrow(() -> new NotFoundException("StructureDefinition not found: " + id));
    } else {
      sd = structureDefinitionService.query(new StructureDefinitionQueryParams().setCode(id).setVersion(version).all())
          .getData()
          .stream()
          .findFirst()
          .orElseThrow(() -> new NotFoundException("StructureDefinition not found: " + id + "; version=" + version));
    }
    return getContent(sd);
  }

  public List<ResourceContent> getContent(StructureDefinition sd) {
    String fhirId = getFhirId(sd.getCode(), sd.getVersion());
    final String json = JsonUtil.toPrettyJson(JsonUtil.toMap(sd.getContent()));
    return List.of(new ResourceContent(fhirId + ".json", json));
  }

  private String prepareVersion(String version) {
    if (StringUtils.isNotBlank(version) && "null".equals(version.trim())) {
      return null;
    }
    return version;
  }
}
