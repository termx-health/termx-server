package com.kodality.termserver.loinc.utils;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.loinc.utils.LoincConcept.LoincConceptProperty;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemContent;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestCodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class LoincMapper {
  private static final String SUBSUMES = "subsumes";
  private static final String DISPLAY = "display";
  private static final String DEFINITION = "definition";

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
        .setNames(new LocalizedName(Map.of("en", "LOINC")))
        .setContent(CodeSystemContent.complete)
        .setCaseSensitive(CaseSignificance.entire_term_case_insensitive)
        .setSupportedLanguages(List.of(Language.en));
  }

  private static CodeSystemImportRequestVersion toVersion(String version) {
    return new CodeSystemImportRequestVersion()
        .setVersion(version)
        .setSource("Regenstrief Institute, Inc.")
        .setSupportedLanguages(List.of(Language.en))
        .setReleaseDate(LocalDate.now());
  }

  private static List<Pair<String, String>> toProperties(List<LoincConcept> concepts) {
    List<Pair<String, String>> properties = concepts.stream()
        .flatMap(c -> c.getProperties().stream()).collect(Collectors.toSet()).stream()
        .collect(Collectors.toMap(LoincConceptProperty::getName, p -> p, (p, q) -> p)).values().stream()
        .map(property -> Pair.of(property.getName(), property.getType()))
        .collect(Collectors.toList());
    properties.add(Pair.of(DISPLAY, EntityPropertyType.string));
    properties.add(Pair.of(DEFINITION, EntityPropertyType.string));
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
      designations.add(mapDesignation(c.getDisplay(), DISPLAY));
    }
    return designations;
  }

  private static Designation mapDesignation(String name, String type) {
    return new Designation()
        .setName(name)
        .setLanguage(Language.en)
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
        .setTargetCode(a.getTargetCode())).toList();
  }

}
