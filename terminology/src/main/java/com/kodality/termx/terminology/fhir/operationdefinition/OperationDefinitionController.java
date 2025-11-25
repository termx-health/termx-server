package com.kodality.termx.terminology.fhir.operationdefinition;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpResponse;
import io.micronaut.core.io.ResourceLoader;

import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;

@Controller("/fhir")
public class OperationDefinitionController {

    @Inject
    ResourceLoader resourceLoader;

    @Get(uri="/OperationDefinition", produces=MediaType.APPLICATION_JSON)
    public HttpResponse<String> getOperationDefinition() throws Exception {
        var resourceOpt = resourceLoader.getResource("classpath:OperationDefinition.json");
        if (resourceOpt.isEmpty()) {
            return HttpResponse.notFound();
        }
        try (var inputStream = resourceOpt.get().openStream()) {
            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return HttpResponse.ok(json)
                .contentType("application/fhir+json");
        }
    }
}