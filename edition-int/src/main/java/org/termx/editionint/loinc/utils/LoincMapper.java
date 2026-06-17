package org.termx.editionint.loinc.utils;

import com.kodality.commons.model.LocalizedName;
import org.termx.editionint.loinc.utils.LoincConcept.LoincConceptProperty;
import org.termx.ts.CaseSignificance;
import org.termx.ts.Language;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystemAssociation;
import org.termx.ts.codesystem.CodeSystemContent;
import org.termx.ts.codesystem.CodeSystemImportRequest;
import org.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestCodeSystem;
import org.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import org.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestProperty;
import org.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestVersion;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityPropertyKind;
import org.termx.ts.codesystem.EntityPropertyRule;
import org.termx.ts.codesystem.EntityPropertyType;
import org.termx.ts.codesystem.EntityPropertyValue;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class LoincMapper {
  private static final String PART_OF = "part-of";
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
        .setSupportedLanguages(List.of(Language.en));
  }

  private static CodeSystemImportRequestVersion toVersion(String version) {
    return new CodeSystemImportRequestVersion()
        .setVersion(version)
        .setSupportedLanguages(List.of(Language.en))
        .setReleaseDate(LocalDate.now());
  }

  private static List<CodeSystemImportRequestProperty> toProperties(List<LoincConcept> concepts) {
    // Collect into a MUTABLE ArrayList — Stream.toList() returns an unmodifiable list (Java 16+),
    // so the subsequent .add(DISPLAY) / .add(KEY_WORDS) calls below would otherwise throw
    // UnsupportedOperationException. The previous run hit this only after the giant-VALUES
    // INSERT bug in CodeSystemEntityRepository.batchUpsert was fixed and the import reached
    // the LOINC concept code-system import for the first time.
    List<CodeSystemImportRequestProperty> properties = concepts.stream()
        .flatMap(c -> c.getProperties().stream())
        .collect(Collectors.groupingBy(LoincConceptProperty::getName)).entrySet().stream()
        .map(e -> {
          LoincConceptProperty sample = e.getValue().get(0);
          CodeSystemImportRequestProperty property = new CodeSystemImportRequestProperty()
              .setName(e.getKey()).setType(sample.getType()).setKind(EntityPropertyKind.property);
          // Coding-typed property values (e.g. answer-list) reference an external code system.
          // Record those systems on the property definition so they survive import and the FHIR
          // export can emit codesystem-property-codesystem. Issue #48.
          if (EntityPropertyType.coding.equals(sample.getType())) {
            List<String> codeSystems = e.getValue().stream()
                .map(LoincConceptProperty::getValue)
                .filter(Concept.class::isInstance).map(Concept.class::cast)
                .map(Concept::getCodeSystem).filter(Objects::nonNull)
                .distinct().toList();
            if (!codeSystems.isEmpty()) {
              property.setRule(new EntityPropertyRule().setCodeSystems(codeSystems));
            }
          }
          return property;
        })
        .collect(Collectors.toCollection(ArrayList::new));
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
      c.getDisplay().forEach((key, value) -> designations.add(mapDesignation(value, DISPLAY, key)));
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
      return new ArrayList<>();
    }
    return c.getAssociations().stream().map(a -> new CodeSystemAssociation()
        .setAssociationType(PART_OF)
        .setStatus(PublicationStatus.active)
        .setOrderNumber(a.getOrder())
        .setTargetCode(a.getTargetCode())).toList();
  }

}
