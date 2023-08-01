package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.modeler.Privilege;
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
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;

@Validated
@RequiredArgsConstructor
@Controller("/transformation-definitions")
public class TransformationDefinitionController {
  private final TransformationDefinitionService service;
  private final TransformerService transformerService;

  @Authorized(Privilege.M_VIEW)
  @Get(uri = "/{id}")
  public TransformationDefinition load(@PathVariable Long id) {
    return service.load(id);
  }

  @Authorized(Privilege.M_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<TransformationDefinition> search(TransformationDefinitionQueryParams params) {
    return service.search(params);
  }

  @Authorized(Privilege.M_EDIT)
  @Post
  public TransformationDefinition create(@Valid @Body TransformationDefinition def) {
    def.setId(null);
    service.save(def);
    return def;
  }

  @Authorized(Privilege.M_EDIT)
  @Put(uri = "/{id}")
  public TransformationDefinition update(@PathVariable Long id, @Valid @Body TransformationDefinition def) {
    def.setId(id);
    service.save(def);
    return def;
  }

  @Authorized(Privilege.M_EDIT)
  @Delete(uri = "/{id}")
  public HttpResponse<?> delete(@PathVariable Long id) {
    service.delete(id);
    return HttpResponse.noContent();
  }

  @Post("{id}/transform")
  public TransformationResult transformInstance(@PathVariable Long id, @Body InstanceTransformationRequest req) {
    return transformerService.transform(req.source, load(id));
  }

  @Post("/transform")
  public TransformationResult transform(@Body TransformationRequest req) {
    return transformerService.transform(req.source, req.definition);
  }

  @Post("/parse-fml")
  public ParseResponse parse(@Body ParseRequest req) {
    try {
      return new ParseResponse(new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(transformerService.parseFml(req.fml)), null);
    } catch (FHIRException | IOException e) {
      return new ParseResponse(null, e.getMessage());
    }
  }

  @Post("/generate-fml")
  public FmlGenerationResult generateFml(@Body TransformationDefinition definition) {
    return new FmlGenerationResult(transformerService.generateFml(definition));
  }

  public record TransformationRequest(TransformationDefinition definition, String source) {}

  public record InstanceTransformationRequest(String source) {}

  public record FmlGenerationResult(String fml) {}

  public record ParseRequest(String fml) {}

  public record ParseResponse(String json, String error) {}
}
