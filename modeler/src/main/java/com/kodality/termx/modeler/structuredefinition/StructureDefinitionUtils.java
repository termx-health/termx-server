package com.kodality.termx.modeler.structuredefinition;

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
      target.setContentType(resource.getKind().toCode());
      target.setContent(json);
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
          .setCode(resource.getId())
          .setContentType(resource.getKind().toCode())
          .setContentFormat("json")
          .setContent(json)
//        .setParent(resource.?)
          .setVersion(resource.getVersion());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
