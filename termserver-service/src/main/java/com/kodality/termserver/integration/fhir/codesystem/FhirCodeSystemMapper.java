package com.kodality.termserver.integration.fhir.codesystem;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyType;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.Property;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FhirCodeSystemMapper {

  public static CodeSystem mapCodeSystem(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(fhirCodeSystem.getId());
    codeSystem.setUri(fhirCodeSystem.getUrl());
    codeSystem.setNames(new LocalizedName(Map.of(Language.en, fhirCodeSystem.getTitle())));
    codeSystem.setDescription(fhirCodeSystem.getDescription());

    codeSystem.setVersions(mapVersion(fhirCodeSystem));
    return codeSystem;
  }

  private static List<CodeSystemVersion> mapVersion(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(fhirCodeSystem.getId());
    version.setVersion(fhirCodeSystem.getVersion());
    version.setSource(fhirCodeSystem.getPublisher());
    version.setPreferredLanguage(Language.en);
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(LocalDate.from(fhirCodeSystem.getDate()));
    return List.of(version);
  }

  public static List<EntityProperty> mapProperties(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    List<EntityProperty> defaultProperties = new ArrayList<>();
    defaultProperties.add(new EntityProperty().setName("display").setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
    defaultProperties.add(new EntityProperty().setName("definition").setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
    if (fhirCodeSystem.getProperty() == null) {
      return defaultProperties;
    }

    List<EntityProperty> designationProperties = fhirCodeSystem.getConcept().stream()
        .filter(c -> c.getDesignation() != null)
        .flatMap(c -> c.getDesignation().stream())
        .filter(d -> d.getUse() != null && d.getUse().getDisplay() != null)
        .map(d -> new EntityProperty().setName(d.getUse().getDisplay()).setType(EntityPropertyType.string).setStatus(PublicationStatus.active)).toList();

    List<EntityProperty> properties = fhirCodeSystem.getProperty().stream().map(p -> {
      EntityProperty property = new EntityProperty();
      property.setName(p.getCode());
      property.setDescription(p.getDescription());
      property.setType(p.getType());
      property.setStatus(PublicationStatus.active);
      return property;
    }).collect(Collectors.toList());

    properties.addAll(defaultProperties);
    properties.addAll(designationProperties);
    return properties;
  }
  public static List<Concept> mapConcepts(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept> fhirConcepts,
                                          com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem,
                                          List<EntityProperty> properties,
                                          com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept parent) {
    List<Concept> concepts = new ArrayList<>();
    fhirConcepts.forEach(c -> {
      Concept concept = new Concept();
      concept.setCode(c.getCode());
      concept.setCodeSystem(fhirCodeSystem.getId());
      concept.setVersions(mapConceptVersion(c, fhirCodeSystem, properties, parent));
      concepts.add(concept);
      if (c.getConcept() != null) {
        concepts.addAll(mapConcepts(c.getConcept(), fhirCodeSystem, properties, c));
      }
    });
    return concepts;
  }

  private static List<CodeSystemEntityVersion> mapConceptVersion(com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept c,
                                                                 com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem,
                                                                 List<EntityProperty> properties,
                                                                 com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept parent) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(c.getCode());
    version.setDesignations(mapDesignations(c, codeSystem, properties));
    version.setPropertyValues(mapPropertyValues(c.getProperty(), properties));
    version.setAssociations(mapAssociations(parent, codeSystem));
    version.setStatus(PublicationStatus.draft);
    return List.of(version);
  }

  private static List<Designation> mapDesignations(com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept c,
                                                   com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem,
                                                   List<EntityProperty> properties) {
    String caseSignificance = codeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive : CaseSignificance.entire_term_case_insensitive;

    Designation display = new Designation();
    display.setDesignationTypeId(properties.stream().filter(p -> p.getName().equals("display")).findFirst().map(EntityProperty::getId).orElse(null));
    display.setName(c.getDisplay());
    display.setPreferred(true);
    display.setLanguage(Language.en);
    display.setCaseSignificance(caseSignificance);
    display.setDesignationKind("text");
    display.setStatus("active");

    Designation definition = new Designation();
    definition.setDesignationTypeId(properties.stream().filter(p -> p.getName().equals("definition")).findFirst().map(EntityProperty::getId).orElse(null));
    definition.setName(c.getDefinition());
    definition.setLanguage(Language.en);
    definition.setCaseSignificance(caseSignificance);
    definition.setDesignationKind("text");
    definition.setStatus("active");

    List<Designation> designations = new ArrayList<>();
    designations.add(display);
    designations.add(definition);

    if (c.getDesignation() == null) {
      return designations;
    }

    designations.addAll(c.getDesignation().stream().map(d -> {
      Designation designation = new Designation();
      designation.setDesignationTypeId(properties.stream().filter(p -> p.getName().equals(d.getUse().getDisplay())).findFirst().map(EntityProperty::getId).orElse(null));
      designation.setName(d.getValue());
      designation.setLanguage(d.getLanguage());
      designation.setCaseSignificance(caseSignificance);
      designation.setDesignationKind("text");
      designation.setStatus("active");
      return designation;
    }).toList());

    return designations;
  }

  private static List<EntityPropertyValue> mapPropertyValues(List<Property> propertyValues, List<EntityProperty> properties) {
    if (propertyValues == null) {
      return new ArrayList<>();
    }
    return propertyValues.stream().map(v -> {
      EntityPropertyValue value = new EntityPropertyValue();
      value.setValue(Stream.of(
          v.getValueCode(), v.getValueCoding(),
          v.getValueString(), v.getValueInteger(),
          v.getValueBoolean(), v.getValueDateTime(), v.getValueDecimal()
      ).filter(Objects::nonNull).findFirst().orElse(null));
      value.setEntityPropertyId(properties.stream().filter(p -> p.getName().equals(v.getCode())).findFirst().map(EntityProperty::getId).orElse(null));
      return value;
    }).collect(Collectors.toList());
  }

  private static List<CodeSystemAssociation> mapAssociations(com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept parent,
                                                             com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    if (parent == null) {
      return new ArrayList<>();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(codeSystem.getHierarchyMeaning());
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(parent.getCode());
    association.setCodeSystem(codeSystem.getId());
    return List.of(association);
  }
}
