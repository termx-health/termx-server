package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.client.HttpClient;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.ValidationEngine.ValidationEngineBuilder;

@RequiredArgsConstructor
@Singleton
public class TransformerService {
  private final StructureDefinitionService structureDefinitionService;
  private final ResourceLoader resourceLoader;
  private final HttpClient httpClient = new HttpClient();
  private ValidationEngine engine;

  @PostConstruct
  public void init() {
    try {
      engine = new ValidationEngineBuilder().fromNothing();
      // looks fishy
      ((Bundle) parse(new String(resourceLoader.getResources("conformance/base/profile-types.json").findFirst().orElseThrow().openStream().readAllBytes())))
          .getEntry().forEach(e -> engine.getContext().cacheResource(e.getResource()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public TransformationResult transform(String source, TransformationDefinition def) {
    try {
      StructureMap sm = getStructureMap(def.getMapping());
      ValidationEngine eng = new ValidationEngine(engine);
      eng.getContext().cacheResource(sm);
      def.getResources().forEach(res -> eng.getContext().cacheResource(parse(getContent(res))));
      String result = transform(eng, source, sm.getUrl());
      return new TransformationResult().setResult(result);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (FHIRException fe) {
      return new TransformationResult().setError(fe.getMessage());
    }
  }

  private StructureMap getStructureMap(TransformationDefinitionResource res) throws FHIRFormatError {
    String content = getContent(res);
    if (content.startsWith("///")) { //XXX not sure if this is what defines Fhir Mapping Language
      return new StructureMapUtilities(engine.getContext()).parse(content, "map");
    }
    return parse(content);
  }

  private String getContent(TransformationDefinitionResource res) {
    return switch (res.getSource()) {
      case "static" -> res.getReference().getContent();
      case "definition" -> structureDefinitionService.load(res.getReference().getStructureDefinitionId()).orElseThrow().getContent();
      case "fhir" -> queryResource(res.getReference().getFhirServer(), res.getReference().getFhirResource());
      default -> throw new RuntimeException("unknown resouce source");
    };
  }

  private String queryResource(String fhirServerUrl, String resource) {
    return httpClient.GET(fhirServerUrl + "/" + resource).thenApply(HttpResponse::body).join();
  }

  private <R extends Resource> R parse(String input) throws FHIRFormatError {
    try {
      if (input.startsWith("<")) {
        return (R) new XmlParser().parse(input);
      }
      return (R) new JsonParser().parse(input);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String transform(ValidationEngine eng, String input, String mapUri) throws FHIRException, IOException {
    Element transformed = eng.transform(input.getBytes(StandardCharsets.UTF_8), FhirFormat.JSON, mapUri);
    ByteArrayOutputStream boas = new ByteArrayOutputStream();
    new org.hl7.fhir.r5.elementmodel.JsonParser(eng.getContext()).compose(transformed, boas, OutputStyle.PRETTY, null);
    String result = boas.toString(StandardCharsets.UTF_8);
    boas.close();
    return result;
  }

}
