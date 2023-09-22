package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.client.HttpClient;
import com.kodality.commons.client.HttpClientError;
import com.kodality.termx.fhir.conceptmap.ConceptMapResourceStorage;
import com.kodality.termx.modeler.ApiError;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.ValidationEngine.ValidationEngineBuilder;

@RequiredArgsConstructor
@Singleton
public class TransformerService {
  private final StructureDefinitionService structureDefinitionService;
  private final ConceptMapResourceStorage conceptMapFhirService;
  private final TransformationDefinitionService structureMapService;
  private final ResourceLoader resourceLoader;
  private final HttpClient httpClient = new HttpClient();
  private ValidationEngine engine;
  @Value("${micronaut.server.port}")
  private String port;

  @PostConstruct
  public void init() {
    try {
      engine = new ValidationEngineBuilder().fromNothing();
      engine.connectToTSServer("http://localhost:" + port + "/fhir", null, FhirPublication.R5);
      loadBaseResources().getEntry().forEach(e -> engine.getContext().cacheResource(e.getResource()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Bundle loadBaseResources() {
    try {
      return parse(new String(resourceLoader.getResources("conformance/base/profile-types.json").findFirst().orElseThrow().openStream().readAllBytes()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public TransformationResult transform(String source, TransformationDefinition def) {
    try {
      ValidationEngine eng = new ValidationEngine(engine);

      for (TransformationDefinitionResource res : def.getResources()) {
        try {
          eng.getContext().cacheResource(parse(getContent(res)));
        } catch (FHIRException e) {
          return new TransformationResult().setError("Invalid resource " + res.getName() + ": " + e.getMessage());
        }
      }

      StructureMap sm;
      try {
        sm = getStructureMap(def.getMapping());
        prepareStructureMap(eng, sm);
        eng.getContext().cacheResource(sm);
      } catch (FHIRException e) {
        return new TransformationResult().setError("Invalid structure map: " + e.getMessage());
      }

      String result = transform(eng, source, sm.getUrl());
      return new TransformationResult().setResult(result);
    } catch (IOException | FHIRException e) {
      if (e instanceof FHIRException err && err.getCause() instanceof NullPointerException) {
        // fixme: is there better way to detect 'StructureDefinition.getSnapshot()" because "sd" is null' error
        return new TransformationResult().setError("Transformation error: " + ApiError.MO102.getMessage());
      }
      return new TransformationResult().setError("Transformation error: " + e.getMessage());
    }
  }

  public List<StructureDefinition> transformDefinitionResource(List<TransformationDefinitionResource> resources) {
    try {
      ValidationEngine eng = new ValidationEngine(engine);
      ContextUtilities cu = new ContextUtilities(eng.getContext());

      return resources.stream()
          .filter(r -> "definition".equals(r.getType()))
          .map(r -> (StructureDefinition) parse(getContent(r)))
          .peek(sd -> {
            if (!sd.hasSnapshot()) {
              cu.generateSnapshot(sd, sd.getKind() != null && sd.getKind() == StructureDefinitionKind.LOGICAL);
            }
          })
          .toList();
    } catch (IOException | FHIRException e) {
      return List.of();
    }
  }

  private void prepareStructureMap(ValidationEngine eng, StructureMap sm) {
    // this should not be needed. ValidationEngine#getSourceResourceFromStructureMap searches for definition by alias, however alias is nullable. workaround.
    sm.getStructure().stream().filter(s -> s.getAlias() == null)
        .forEach(s -> s.setAlias(eng.getContext().listStructures().stream()
            .filter(sd -> sd.getUrl().equals(s.getUrl())).findFirst().orElseThrow().getName()));

    //fix snapshots?
    ContextUtilities cu = new ContextUtilities(eng.getContext());
    cu.allStructures().stream().filter(sd -> !sd.hasSnapshot())
        .forEach(sd -> cu.generateSnapshot(sd, sd.getKind() != null && sd.getKind() == StructureDefinitionKind.LOGICAL));
  }

  private StructureMap getStructureMap(TransformationDefinitionResource res) {
    String content = getContent(res);
    if (content.startsWith("///")) { //XXX not sure if this is what defines Fhir Mapping Language
      return parseFml(content);
    }
    return parse(content);
  }

  public String getContent(TransformationDefinitionResource res) {
    return switch (res.getSource()) {
      case "static" -> res.getReference().getContent();
      case "url" -> queryResource(res.getReference().getResourceUrl());
      case "local" -> switch (res.getType()) {
        case "definition" -> structureDefinitionService.load(Long.valueOf(res.getReference().getLocalId())).orElseThrow().getContent();
        case "conceptmap" -> conceptMapFhirService.load(res.getReference().getLocalId()).getContent().getValue();
        case "mapping" -> {
          StructureMap structureMap = getStructureMap(structureMapService.load(Long.valueOf(res.getReference().getLocalId())).getMapping());
          try {
            yield new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(structureMap);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
        default -> throw new RuntimeException("unknown type: " + res.getType());
      };
      default -> throw new RuntimeException("unknown resource source " + res.getSource());
    };
  }

  private String queryResource(String url) {
    return httpClient.GET(url).thenApply(HttpResponse::body).exceptionally(e -> {
      if (e.getCause() instanceof HttpClientError err) {
        if (300 <= err.getResponse().statusCode() && err.getResponse().statusCode() < 400) {
          throw ApiError.MO101.toApiException();
        }
      }
      throw new RuntimeException("Error occurred when querying resource", e.getCause());
    }).join();
  }

  private <R extends Resource> R parse(String input) {
    try {
      if (input.startsWith("<")) {
        return (R) new XmlParser().parse(input);
      }
      return (R) new JsonParser().parse(input);
    } catch (Exception e) {
      throw new FHIRException(e.getMessage(), e);
    }
  }

  public StructureMap parseFml(String content) {
    StructureMap map = new StructureMapUtilities(engine.getContext()).parse(content, "map");
    map.getText().setStatus(NarrativeStatus.GENERATED);
    map.getText().setDiv(new XhtmlNode(NodeType.Element, "div"));
    String render = StructureMapUtilities.render(map);
    map.getText().getDiv().addTag("pre").addText(render);
    return map;
  }

  private String transform(ValidationEngine eng, String input, String mapUri) throws IOException {
    FhirFormat format = input.startsWith("<") ? FhirFormat.XML : FhirFormat.JSON;
    Element transformed = eng.transform(input.getBytes(StandardCharsets.UTF_8), format, mapUri);
    ByteArrayOutputStream boas = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.JsonParser(eng.getContext()).compose(transformed, boas, OutputStyle.PRETTY, null);
    String result = boas.toString(StandardCharsets.UTF_8);
    boas.close();
    return result;
  }

  public String composeFml(TransformationDefinition definition) {
    List<String> rows = new ArrayList<>();
    rows.add("/// url = 'http://hl7.org/fhir/StructureMap/" + definition.getName() + "'");
    rows.add("/// name = '" + definition.getName() + "'");
    rows.add("/// title = '" + definition.getName() + "'");
    rows.add("");

    if (CollectionUtils.isNotEmpty(definition.getResources())) {
      List<StructureDefinition> definitions =
          definition.getResources().stream().filter(r -> r.getType().equals("definition")).map(x -> this.<StructureDefinition>parse(getContent(x))).toList();
      definitions.subList(0, definitions.size() - 1).forEach(source -> {
        rows.add("uses \"" + source.getUrl() + "\" alias " + source.getName() + " as source");
      });
      StructureDefinition target = definitions.get(definitions.size() - 1);
      rows.add("uses \"" + target.getUrl() + "\" alias " + target.getName() + " as target");


      List<TransformationDefinitionResource> mappingResources = definition.getResources().stream().filter(r -> r.getType().equals("mapping")).toList();
      if (CollectionUtils.isNotEmpty(mappingResources)) {
        rows.add("");
        mappingResources.stream().map(res -> this.<StructureMap>parse(getContent(res))).forEach(sm -> {
          rows.add("imports \"" + sm.getUrl() + "\"");
        });
      }

      rows.add("");
      rows.add("group example(source src : " + definitions.get(0).getName() + ", target tgt : " + target.getName() + ") {");
      rows.add("  ");
      rows.add("}");
    }
    return rows.stream().collect(Collectors.joining("\n"));
  }
}
