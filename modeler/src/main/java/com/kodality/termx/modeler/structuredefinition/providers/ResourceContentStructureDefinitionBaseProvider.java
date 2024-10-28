package com.kodality.termx.modeler.structuredefinition.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public abstract class ResourceContentStructureDefinitionBaseProvider implements ResourceContentProvider {

  private final StructureDefinitionService structureDefinitionService;

  public abstract List<ResourceContentProvider.ResourceContent> getContent(String id, String version);

  @Override
  public List<ResourceContent> getContent(String idVersionPipe) {
    String[] pipe = PipeUtil.parsePipe(idVersionPipe);
    return getContent(pipe[0], prepareVersion(pipe[1]));
  }

  protected static String prepareVersion(String version) {
    if (StringUtils.isNotBlank(version) && "null".equals(version.trim())) {
      return null;
    }
    return version;
  }

  protected StructureDefinition getStructureDefinition(String id, String version) {
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
    return sd;
  }
}
