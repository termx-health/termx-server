package com.kodality.termx.modeler.transformationdefinition.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionQueryParams;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

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
    final Optional<ResourceContent> fmlResource = getFmlResource(content, td.getName());
    final Optional<ResourceContent> jsonResource = Optional.of(new ResourceContent(td.getName() + ".json", content));
    return Stream.of(jsonResource, fmlResource)
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<ResourceContent> getFmlResource(String content, String transformationDefinitionName) {
    if (content.startsWith("{")) {
      final Map<String, Object> contentMap = JsonUtil.toMap(content);
      final Optional<Map<String, Object>> text = ofNullable((Map<String, Object>)contentMap.get("text"));
      return text.flatMap(txt -> {
        final String fml = (String) txt.get("div");
        if (StringUtils.isBlank(fml)) {
          return Optional.empty();
        }
        return Optional.of(new ResourceContent(transformationDefinitionName + ".fml", prepareFml(fml)));
      });
    }
    return Optional.empty();
  }

  private static String prepareFml(String fml) {
    return fml.replaceAll("^<div>", "")
        .replaceAll("</div>$", "")
        .replaceAll("^\n", "")
        .replaceAll("^\r", "")
        .replaceAll("\n$", "")
        .replaceAll("\r$", "");
  }
}
