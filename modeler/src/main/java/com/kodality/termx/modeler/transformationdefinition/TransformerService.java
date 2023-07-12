package com.kodality.termx.modeler.transformationdefinition;

import ch.ahdis.matchbox.engine.MatchboxEngine;
import ch.ahdis.matchbox.engine.MatchboxEngine.MatchboxEngineBuilder;
import com.kodality.commons.client.HttpClient;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.http.HttpResponse;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureMap;

@RequiredArgsConstructor
@Singleton
public class TransformerService {
  private final StructureDefinitionService structureDefinitionService;
  private final ResourceLoader resourceLoader;
  private final HttpClient httpClient = new HttpClient();
  private MatchboxEngine baseEngine;

  @PostConstruct
  public void init() {
    try {
      baseEngine = new MatchboxEngineBuilder().getEngine();
      baseEngine.setVersion("5.0");
      baseEngine.setDebug(true);
      // looks fishy
      ((Bundle) parse(new String(resourceLoader.getResources("conformance/base/profile-types.json").findFirst().orElseThrow().openStream().readAllBytes())))
          .getEntry().forEach(e -> baseEngine.addCanonicalResource((CanonicalResource) e.getResource()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public TransformationResult transform(String source, TransformationDefinition def) {
    try {
      MatchboxEngine engine = new MatchboxEngine(baseEngine);
      StructureMap sm = getStructureMap(def.getMapping(), engine);
      engine.addCanonicalResource(sm);
      def.getResources().forEach(res -> engine.addCanonicalResource(parse(getContent(res))));
      String result = engine.transform(source, true, sm.getUrl(), true);
      return new TransformationResult().setResult(result);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (FHIRException fe) {
      return new TransformationResult().setError(fe.getMessage());
    }
  }

  private StructureMap getStructureMap(TransformationDefinitionResource res, MatchboxEngine engine) throws FHIRFormatError {
    String content = getContent(res);
    if (content.startsWith("///")) { //XXX not sure if this is what defines Fhir Mapping Language
      return baseEngine.parseMapR5(content);
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

}
