package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.client.HttpClient;
import com.kodality.commons.client.HttpClientError;
import com.kodality.termx.core.sys.server.httpclient.TerminologyServerHttpClientService;
import com.kodality.termx.modeler.ApiError;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import com.kodality.termx.terminology.fhir.FhirFshConverter;
import com.kodality.termx.terminology.fhir.conceptmap.ConceptMapResourceStorage;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Address;
import com.kodality.zmei.fhir.datatypes.Age;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Duration;
import com.kodality.zmei.fhir.datatypes.HumanName;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Range;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.Timing;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.elementmodel.ParserBase;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.ElementDefinition;
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

import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceSource.local;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceSource.statik;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceSource.url;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceType.conceptMap;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceType.definition;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceType.mapping;

@RequiredArgsConstructor
@Singleton
public class TransformerService {
  private final StructureDefinitionService structureDefinitionService;
  private final ConceptMapResourceStorage conceptMapFhirService;
  private final TransformationDefinitionRepository structureMapRepository;
  private final ResourceLoader resourceLoader;
  private final TerminologyServerHttpClientService httpClientService;
  private final Optional<FhirFshConverter> fshConverter;
  private final HttpClient httpClient = new HttpClient();
  private ValidationEngine engine;
  @Value("${micronaut.server.port}")
  private String port;

  private synchronized ValidationEngine getEngine() {
    if (engine == null) {
      try {
        engine = new ValidationEngineBuilder().fromNothing();
        engine.connectToTSServer("http://localhost:" + port + "/fhir", null, FhirPublication.R5);
        loadBaseResources().getEntry().forEach(e -> engine.getContext().cacheResource(e.getResource()));
        return engine;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return engine;
  }

  public Bundle loadBaseResources() {
    try {
      return parse(new String(resourceLoader.getResources("conformance/base/profiles-types.json").findFirst().orElseThrow().openStream().readAllBytes()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public TransformationResult transform(String source, TransformationDefinition def) {
    try {
      ValidationEngine eng = new ValidationEngine(getEngine());

      for (TransformationDefinitionResource res : def.getResources()) {
        try {
          StructureDefinition sd = parse(getContent(res));
          // profile-types.json loads StructureDefinitions on startup, skipping duplicates
          if (eng.getContext().getStructure(sd.getName()) == null) {
            eng.getContext().cacheResource(sd);
          }
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

  public List<StructureDefinition> transformResources(List<TransformationDefinitionResource> resources) {
    try {
      ValidationEngine eng = new ValidationEngine(getEngine());
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
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private String transform(ValidationEngine eng, String input, String mapUri) throws IOException {
    FhirFormat format = input.startsWith("<") ? FhirFormat.XML : FhirFormat.JSON;
    Element transformed = eng.transform(input.getBytes(StandardCharsets.UTF_8), format, mapUri);
    ByteArrayOutputStream boas = new ByteArrayOutputStream();
    ParserBase parser = format == FhirFormat.XML
        ? new org.hl7.fhir.r5.elementmodel.XmlParser(eng.getContext())
        : new org.hl7.fhir.r5.elementmodel.JsonParser(eng.getContext());
    parser.compose(transformed, boas, OutputStyle.PRETTY, null);
    String result = boas.toString(StandardCharsets.UTF_8);
    boas.close();
    return result;
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


  public StructureMap getStructureMap(TransformationDefinitionResource res) {
    String content = getContent(res);
    if (content.startsWith("///")) { //XXX not sure if this is what defines Fhir Mapping Language
      return parseFml(content);
    }
    return parse(content);
  }

  public String getContent(TransformationDefinitionResource res) {

    return switch (res.getSource()) {
      case statik -> res.getReference().getContent();
      case url -> queryResource(res.getReference().getResourceUrl(), res.getReference().getResourceServerId());
      case local -> switch (res.getType()) {
        case definition -> {
          var sd = structureDefinitionService.load(Long.valueOf(res.getReference().getLocalId())).orElseThrow();
          if ("fsh".equals(sd.getContentFormat())) {
            yield fshConverter.orElseThrow().toFhir(sd.getContent()).join();
          }
          yield sd.getContent();
        }
        case conceptMap -> conceptMapFhirService.load(res.getReference().getLocalId()).getContent().getValue();
        case mapping -> {
          StructureMap structureMap = getStructureMap(structureMapRepository.load(Long.valueOf(res.getReference().getLocalId())).getMapping());
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

  private String queryResource(String path, Long serverId) {
    HttpClient client = serverId == null ? httpClient : httpClientService.getHttpClient(serverId);
    return client.GET(path).thenApply(HttpResponse::body).exceptionally(e -> {
      if (e.getCause() instanceof HttpClientError err) {
        if (300 <= err.getResponse().statusCode() && err.getResponse().statusCode() < 400) {
          throw ApiError.MO101.toApiException();
        }
      }
      throw new RuntimeException("Error occurred when querying resource", e.getCause());
    }).join();
  }

  public <R extends Resource> R parse(String input) {
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
    StructureMap map = new StructureMapUtilities(getEngine().getContext()).parse(content, "map");
    map.getText().setStatus(NarrativeStatus.GENERATED);
    map.getText().setDiv(new XhtmlNode(NodeType.Element, "div"));
    String render = StructureMapUtilities.render(map);
    map.getText().getDiv().addTag("pre").addText(render);
    return map;
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


  public String generateObject(TransformationDefinitionResource resource) {
    return generateObject(getContent(resource));
  }

  public String generateObject(String definition) {
    return generateObject(parse(definition));
  }

  public String generateObject(StructureDefinition definition) {
    if (!definition.hasSnapshot()) {
      ContextUtilities cu = new ContextUtilities(getEngine().getContext());
      cu.generateSnapshot(definition, definition.getKind() != null && definition.getKind() == StructureDefinitionKind.LOGICAL);
    }
    Map<String, Object> resource = new LinkedHashMap<>();
    resource.put(definition.getName(), new LinkedHashMap<>(Map.of("resourceType", definition.getName())));
    definition.getSnapshot().getElement().forEach(el -> {
      String[] path = StringUtils.substringBeforeLast(el.getPath(), ".").split("\\.");
      Map<String, Object> parent = resource;
      for (String p : path) {
        Object v = parent.computeIfAbsent(p, x -> new LinkedHashMap<>());
        parent = (Map<String, Object>) (v instanceof List l ? l.get(0) : v);
      }
      String name = StringUtils.substringAfterLast(el.getPath(), ".");
      Object value = generateValue(name, el);
      if (value != null) {
        parent.put(name, "*".equals(el.getMax()) ? List.of(value) : value);
      }
    });
    return FhirMapper.toJson(resource.get(definition.getName()), true);
  }

  private Object generateValue(String name, ElementDefinition el) {
    if (CollectionUtils.isEmpty(el.getType())) {
      return null;
    }
    String code = el.getType().get(0).getCode();
    return switch (code) {
      case "string", "code", "id", "markdown" -> name + "-" + randomString();
      case "oid" -> "urn:oid:" + UUID.randomUUID();
      case "uri", "uuid" -> "urn:uuid:" + UUID.randomUUID();
      case "url" -> "http://" + name + "-" + randomString();
      case "dateTime", "instant" -> OffsetDateTime.now();
      case "date" -> LocalDate.now();
      case "time" -> LocalTime.now();
      case "boolean" -> new Random().nextBoolean();
      case "integer", "integer64", "unsignedInt", "positiveInt" -> Math.abs(new Random().nextInt());
      case "decimal" -> new Random().nextFloat();

      case "BackboneElement" -> new LinkedHashMap<>();
      case "Identifier" -> new Identifier().setValue(randomString(16));
      case "HumanName" -> new HumanName().setGiven(List.of(randomString())).setFamily(randomString());
      case "Address" -> new Address().setText(randomString() + " " + randomString() + " " + randomString());
      case "ContactPoint" -> new ContactPoint().setValue(randomString());
      case "Timing" -> new Timing();
      case "Quantity" -> new Quantity().setValue(randomBigDecimal());
      case "Range" -> new Range().setLow(new Quantity().setValue(randomBigDecimal())).setHigh(new Quantity().setValue(randomBigDecimal()));
      case "Period" -> new Period().setStart(OffsetDateTime.now()).setEnd(OffsetDateTime.now());
      case "CodeableConcept" -> CodeableConcept.fromCodes(randomString());
      case "Coding" -> new Coding().setCode(randomString());
      case "Age" -> new Age().setValue(randomBigDecimal());
      case "Duration" -> new Duration().setValue(randomBigDecimal());

      case "Reference" -> new Reference("Resource/" + RandomStringUtils.randomNumeric(6));
      case "CodeableReference" -> new CodeableReference().setConcept(CodeableConcept.fromCodes(randomString()));
      default -> null;
    };
  }

  private static BigDecimal randomBigDecimal() {
    return new BigDecimal(Float.toString(new Random().nextFloat()));
  }

  private static String randomString() {
    return randomString(8);
  }

  private static String randomString(int count) {
    return RandomStringUtils.random(count, "qwertyuiopasdfghjklzxcvbnm");
  }
}
