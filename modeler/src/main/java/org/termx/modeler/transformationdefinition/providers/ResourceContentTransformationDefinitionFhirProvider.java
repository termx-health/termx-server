package org.termx.modeler.transformationdefinition.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.github.ResourceContentProvider;
import org.termx.modeler.transformationdefinition.TransformationDefinition;
import org.termx.modeler.transformationdefinition.TransformationDefinitionQueryParams;
import org.termx.modeler.transformationdefinition.TransformationDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Singleton
@RequiredArgsConstructor
public class ResourceContentTransformationDefinitionFhirProvider implements ResourceContentProvider {

  private final TransformationDefinitionService transformationDefinitionService;

  @Override
  public String getResourceType() {
    return "StructureMap";
  }

  @Override
  public String getContentType() {
    return "fhir";
  }

  @Override
  public List<ResourceContent> getContent(String idVersionPipe) {
    String[] pipe = PipeUtil.parsePipe(idVersionPipe);
    final String id = pipe[0];
    TransformationDefinition td;
    if (StringUtils.isNumeric(id)) {
      td = transformationDefinitionService.load(Long.parseLong(id));
    } else {
      td = transformationDefinitionService.search(new TransformationDefinitionQueryParams().setName(id).all())
          .getData()
          .stream()
          .findFirst()
          .orElseThrow(() -> new NotFoundException("TransformationDefinition not found: " + id));
    }
    return getContent(td);
  }

  public List<ResourceContent> getContent(TransformationDefinition td) {
    final String content = td.getMapping().getReference().getContent();
    final ResourceContent jsonResource = new ResourceContent(td.getName() + ".json", content);
    return List.of(jsonResource);
  }
}
