package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.modeler.Privilege;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities;

@Validated
@RequiredArgsConstructor
@Controller("/transformation-definitions")
public class TransformationDefinitionController {
  private final TransformationDefinitionService service;
  private final TransformerService transformerService;
  private final ProvenanceService provenanceService;

  @Authorized(Privilege.TD_VIEW)
  @Get(uri = "/{id}")
  public TransformationDefinition load(@PathVariable Long id) {
    return service.load(id);
  }

  @Authorized(Privilege.TD_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<TransformationDefinition> search(TransformationDefinitionQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.TD_VIEW, Long::valueOf));
    return service.search(params);
  }

  @Authorized(Privilege.TD_EDIT)
  @Post
  public TransformationDefinition create(@Valid @Body TransformationDefinition def) {
    def.setId(null);
    service.save(def);
    provenanceService.create(new Provenance("created", "TransformationDefinition", def.getId().toString()));
    return def;
  }

  @Authorized(Privilege.TD_EDIT)
  @Put(uri = "/{id}")
  public TransformationDefinition update(@PathVariable Long id, @Valid @Body TransformationDefinition def) {
    def.setId(id);
    service.save(def);
    provenanceService.create(new Provenance("modified", "TransformationDefinition", def.getId().toString()));
    return def;
  }

  @Authorized(Privilege.TD_EDIT)
  @Delete(uri = "/{id}")
  public HttpResponse<?> delete(@PathVariable Long id) {
    service.delete(id);
    provenanceService.create(new Provenance("deleted", "TransformationDefinition", id.toString()));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.TD_EDIT)
  @Post(uri = "/{id}/duplicate")
  public TransformationDefinition duplicate(@PathVariable Long id) {
    return service.duplicate(id);
  }

  @Authorized(Privilege.TD_VIEW)
  @Post("{id}/transform")
  public TransformationResult transformInstance(@PathVariable Long id, @Body InstanceTransformationRequest req) {
    return transformerService.transform(req.source, load(id));
  }

  @Authorized
  @Post("/transform")
  public TransformationResult transform(@Body TransformationRequest req) {
    return transformerService.transform(req.source, req.definition);
  }

  @Authorized
  @Post("/transform-resources")
  public String transformResources(@Body List<TransformationDefinitionResource> resources) {
    List<StructureDefinition> defs = transformerService.transformResources(resources);
    return "[" +
           defs.stream().map(r -> {
             try {
               return new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(r);
             } catch (IOException e) {
               throw new RuntimeException(e);
             }
           }).collect(Collectors.joining(",")) +
           "]";
  }

  @Authorized
  @Post("/transform-resource-content")
  public String transformResourceContent(@Body TransformationDefinitionResource res) {
    return transformerService.getContent(res);
  }

  @Authorized
  @Post("/parse-fml")
  public ParseResponse parse(@Body ParseRequest req) {
    try {
      return new ParseResponse(new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(transformerService.parseFml(req.fml)), null);
    } catch (FHIRException | IOException e) {
      return new ParseResponse(null, e.getMessage());
    }
  }

  @Authorized
  @Post("/compose-fml")
  public FmlComposeResult composeFml(@Body TransformationDefinition definition) {
    return new FmlComposeResult(transformerService.composeFml(definition));
  }

  @Authorized
  @Post("/generate-fml")
  public FmlGenerateResult generateFml(@Body FmlGenerateRequest req) {
    try {
      StructureMap map = (StructureMap) new JsonParser().parse(req.structureMap);
      return new FmlGenerateResult(StructureMapUtilities.render(map), null);
    } catch (IOException e) {
      return new FmlGenerateResult(null, e.getMessage());
    }
  }

  @Authorized(privilege = Privilege.TD_VIEW)
  @Post("/generate-input")
  public GenerateInputResponse generateInput(@Body GenerateInputRequest req) {
    return new GenerateInputResponse(transformerService.generateObject(req.resource));
  }

  @Authorized(privilege = Privilege.TD_VIEW)
  @Get(uri = "/base-resources")
  public String loadBaseResources() {
    try {
      return new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(transformerService.loadBaseResources());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public record TransformationRequest(TransformationDefinition definition, String source) {}

  public record InstanceTransformationRequest(String source) {}

  public record FmlComposeResult(String fml) {}

  public record FmlGenerateRequest(String structureMap) {}

  public record FmlGenerateResult(String fml, String error) {}

  public record ParseRequest(String fml) {}

  public record ParseResponse(String json, String error) {}

  public record GenerateInputRequest(TransformationDefinitionResource resource) {}

  public record GenerateInputResponse(String resource) {}
}
