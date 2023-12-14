package com.kodality.termx.fhir;

import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.validation.ResourceProfileValidator;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

@Singleton
@Replaces(ResourceProfileValidator.class)
class TermxResourceProfileValidator extends ResourceProfileValidator {
  @Override
  public void handle(String level, String operation, ResourceContent parameters) {
    if (operation.equals("$transform")) {
      // StructureMap may contain custom StructureDefinition resources.
      return;
    }
    super.handle(level, operation, parameters);
  }

  @Override
  public void handle(ResourceId id, ResourceContent content, String interaction) {
    if (id.getResourceType().equals("StructureMap")){
      // StructureMap may contain custom StructureDefinition resources.
      return;
    }
    super.handle(id, content, interaction);
  }
}
