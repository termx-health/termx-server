package com.kodality.termx.loinc.utils;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.loinc.utils.LoincConcept.LoincConceptProperty;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestCodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestProperty;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LoincMapper {
  private static final String SUBSUMES = "subsumes";
  private static final String DISPLAY = "display";
  private static final String KEY_WORDS = "key-words";

  public static CodeSystemImportRequest toRequest(LoincImportRequest configuration, List<LoincConcept> concepts) {
    CodeSystemImportRequest request = new CodeSystemImportRequest();
    request.setActivate(false);
    request.setCodeSystem(toCodeSystem());
    request.setVersion(toVersion(configuration.getVersion()));

    request.setProperties(toProperties(concepts));
    request.setConcepts(toConcepts(concepts));
    return request;
  }

  private static CodeSystemImportRequestCodeSystem toCodeSystem() {
    return new CodeSystemImportRequestCodeSystem().setId("loinc")
        .setUri("http://loinc.org")
        .setPublisher("Regenstrief Institute, Inc.")
        .setTitle(new LocalizedName(Map.of("en", "LOINC")))
        .setContent(CodeSystemContent.complete)
        .setCaseSensitive(CaseSignificance.entire_term_case_insensitive)
        .setSupportedLanguages(List.of(Language.en))
        .setHierarchyMeaning(SUBSUMES);
  }

  private static CodeSystemImportRequestVersion toVersion(String version) {
    return new CodeSystemImportRequestVersion()
        .setVersion(version)
        .setSupportedLanguages(List.of(Language.en))
        .setReleaseDate(LocalDate.now());
  }

  private static List<CodeSystemImportRequestProperty> toProperties(List<LoincConcept> concepts) {
    List<CodeSystemImportRequestProperty> properties = concepts.stream()
        .flatMap(c -> c.getProperties().stream()).collect(Collectors.toSet()).stream()
        .collect(Collectors.toMap(LoincConceptProperty::getName, p -> p, (p, q) -> p)).values().stream()
        .map(property -> new CodeSystemImportRequestProperty().setName(property.getName()).setType(property.getType()).setKind(EntityPropertyKind.property))
        .collect(Collectors.toList());
    properties.add(new CodeSystemImportRequestProperty().setName(DISPLAY).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation));
    properties.add(new CodeSystemImportRequestProperty().setName(KEY_WORDS).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property));
    return properties;
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(List<LoincConcept> concepts) {
    return concepts.stream().map(LoincMapper::mapConcept).toList();
  }

  private static CodeSystemImportRequestConcept mapConcept(LoincConcept c) {
    CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
    concept.setCode(c.getCode());
    concept.setDesignations(mapDesignations(c));
    concept.setPropertyValues(mapPropertyValues(c));
    concept.setAssociations(mapAssociations(c));
    return concept;
  }

  private static List<Designation> mapDesignations(LoincConcept c) {
    List<Designation> designations = new ArrayList<>();
    if (c.getDisplay() != null) {
      designations.add(mapDesignation(c.getDisplay(), DISPLAY, Language.en));
    }
    if (c.getRelatedNames() != null) {
      designations.addAll(c.getRelatedNames().stream().map(rn -> mapDesignation(rn.getValue(), KEY_WORDS, rn.getKey())).toList());
    }
    return designations;
  }

  private static Designation mapDesignation(String name, String type, String lang) {
    return new Designation()
        .setName(name)
        .setLanguage(lang)
        .setCaseSignificance(CaseSignificance.entire_term_case_insensitive)
        .setDesignationKind("text")
        .setStatus(PublicationStatus.active)
        .setDesignationType(type)
        .setPreferred(DISPLAY.equals(type));
  }

  private static List<EntityPropertyValue> mapPropertyValues(LoincConcept c) {
    return c.getProperties().stream().collect(Collectors.toMap(LoincMapper::getPropertyUniqueKey, p -> p, (p, q) -> p)).values().stream()
        .map(p -> new EntityPropertyValue()
            .setValue(p.getValue())
            .setEntityProperty(p.getName())).toList();
  }

  private static String getPropertyUniqueKey(LoincConceptProperty p) {
    return p.getName() + (p.getValue() instanceof Concept ? ((Concept) p.getValue()).getCode() : String.valueOf(p.getValue()));
  }

  private static List<CodeSystemAssociation> mapAssociations(LoincConcept c) {
    if (c.getAssociations() == null) {
      return List.of();
    }
    return c.getAssociations().stream().map(a -> new CodeSystemAssociation()
        .setAssociationType(SUBSUMES)
        .setStatus(PublicationStatus.active)
        .setOrderNumber(a.getOrder())
        .setTargetCode(a.getTargetCode())).toList();
  }

}
