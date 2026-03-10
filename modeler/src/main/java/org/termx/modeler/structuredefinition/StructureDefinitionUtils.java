package org.termx.modeler.structuredefinition;

import org.termx.modeler.structuredefinition.StructureDefinition;
import org.termx.modeler.structuredefinition.StructureDefinitionQueryParams;

import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4b.formats.JsonParser;

import java.io.IOException;

@UtilityClass
public class StructureDefinitionUtils {

  public static void enrichFromJson(StructureDefinition target, String json) {
    try {
      final org.hl7.fhir.r4b.model.StructureDefinition resource =
          (org.hl7.fhir.r4b.model.StructureDefinition) new JsonParser().parse(json);
      target.setUrl(resource.getUrl());
      target.setName(resource.hasName() ? resource.getName() : null);
      target.setContentType(resource.getKind() != null ? resource.getKind().toCode() : null);
      target.setContent(json);
      target.setParent(resource.hasBaseDefinition() ? resource.getBaseDefinition() : null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void enrichFromDefinition(StructureDefinition target, StructureDefinition source) {
    target.setUrl(source.getUrl());
    target.setContent(source.getContent());
    target.setContentType(source.getContentType());
  }

  public static StructureDefinition createStructureDefinitionFromJson(String json) {
    try {
      final org.hl7.fhir.r4b.model.StructureDefinition resource =
          (org.hl7.fhir.r4b.model.StructureDefinition) new JsonParser().parse(json);
      return new StructureDefinition()
          .setUrl(resource.getUrl())
          .setCode(resource.hasId() ? resource.getId() : null)
          .setName(resource.hasName() ? resource.getName() : null)
          .setParent(resource.hasBaseDefinition() ? resource.getBaseDefinition() : null)
          .setContentType(resource.getKind() != null ? resource.getKind().toCode() : null)
          .setContentFormat("json")
          .setContent(json)
          .setVersion(resource.hasVersion() ? resource.getVersion() : null)
          .setStatus(resource.hasStatus() ? resource.getStatus().toCode() : "draft");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
