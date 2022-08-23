package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.ContactDetail;
import com.kodality.termserver.ContactDetail.Telecom;
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
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;

@Singleton
public class CodeSystemFhirImportMapper {
  private static final String DISPLAY = "display";
  private static final String DEFINITION = "definition";

  public static CodeSystem mapCodeSystem(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(fhirCodeSystem.getId());
    codeSystem.setUri(fhirCodeSystem.getUrl());
    codeSystem.setNames(new LocalizedName(Map.of(Language.en, fhirCodeSystem.getName())));
    codeSystem.setContent(fhirCodeSystem.getContent());
    codeSystem.setContacts(fhirCodeSystem.getContact() == null ? null :
        fhirCodeSystem.getContact().stream().map(CodeSystemFhirImportMapper::mapCodeSystemContact).collect(Collectors.toList()));
    codeSystem.setDescription(fhirCodeSystem.getDescription());
    codeSystem.setCaseSensitive(fhirCodeSystem.getCaseSensitive() != null && fhirCodeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive : CaseSignificance.entire_term_case_insensitive);
    codeSystem.setNarrative(fhirCodeSystem.getText() == null ? null : fhirCodeSystem.getText().getDiv());

    codeSystem.setVersions(mapVersion(fhirCodeSystem));
    codeSystem.setConcepts(mapConcepts(fhirCodeSystem.getConcept(), fhirCodeSystem, null));
    codeSystem.setProperties(mapProperties(fhirCodeSystem));
    return codeSystem;
  }

  private static ContactDetail mapCodeSystemContact(com.kodality.zmei.fhir.datatypes.ContactDetail c) {
    ContactDetail contact = new ContactDetail();
    contact.setName(c.getName());
    contact.setTelecoms(c.getTelecom() == null ? null : c.getTelecom().stream().map(t ->
        new Telecom().setSystem(t.getSystem()).setUse(t.getUse()).setValue(t.getValue())
    ).collect(Collectors.toList()));
    return contact;
  }

  private static List<CodeSystemVersion> mapVersion(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(fhirCodeSystem.getId());
    version.setVersion(fhirCodeSystem.getVersion());
    version.setSource(fhirCodeSystem.getPublisher());
    version.setPreferredLanguage(Language.en);
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(fhirCodeSystem.getDate() == null ? LocalDate.now() : LocalDate.from(fhirCodeSystem.getDate()));
    return List.of(version);
  }

  private static List<EntityProperty> mapProperties(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    List<EntityProperty> defaultProperties = new ArrayList<>();
    defaultProperties.add(new EntityProperty().setName(DISPLAY).setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
    defaultProperties.add(new EntityProperty().setName(DEFINITION).setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
    if (fhirCodeSystem.getProperty() == null) {
      return defaultProperties;
    }

    List<EntityProperty> designationProperties = fhirCodeSystem.getConcept().stream()
        .filter(c -> c.getDesignation() != null)
        .flatMap(c -> c.getDesignation().stream())
        .filter(d -> d.getUse() != null && d.getUse().getCode() != null)
        .map(d -> new EntityProperty().setName(d.getUse().getCode()).setType(EntityPropertyType.string).setStatus(PublicationStatus.active)).toList();

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

  private static List<Concept> mapConcepts(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept> fhirConcepts,
                                          com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem,
                                          com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept parent) {
    List<Concept> concepts = new ArrayList<>();
    if (CollectionUtils.isEmpty(fhirConcepts)) {
      return concepts;
    }
    fhirConcepts.forEach(c -> {
      Concept concept = new Concept();
      concept.setCode(c.getCode());
      concept.setCodeSystem(fhirCodeSystem.getId());
      concept.setVersions(mapConceptVersion(c, fhirCodeSystem, parent));
      concepts.add(concept);
      if (c.getConcept() != null) {
        concepts.addAll(mapConcepts(c.getConcept(), fhirCodeSystem, c));
      }
    });
    return concepts;
  }

  private static List<CodeSystemEntityVersion> mapConceptVersion(com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept c,
                                                                 com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem,
                                                                 com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept parent) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(c.getCode());
    version.setCodeSystem(codeSystem.getId());
    version.setDesignations(mapDesignations(c, codeSystem));
    version.setPropertyValues(mapPropertyValues(c.getProperty()));
    version.setAssociations(mapAssociations(parent, codeSystem));
    version.setStatus(PublicationStatus.draft);
    return List.of(version);
  }

  private static List<Designation> mapDesignations(com.kodality.zmei.fhir.resource.terminology.CodeSystem.Concept c,
                                                   com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    String caseSignificance = codeSystem.getCaseSensitive() != null && codeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive : CaseSignificance.entire_term_case_insensitive;

    Designation display = new Designation();
    display.setDesignationType(DISPLAY);
    display.setName(c.getDisplay());
    display.setPreferred(true);
    display.setLanguage(Language.en);
    display.setCaseSignificance(caseSignificance);
    display.setDesignationKind("text");
    display.setStatus("active");

    Designation definition = new Designation();
    definition.setDesignationType(DEFINITION);
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
      designation.setDesignationType(d.getUse() == null ? DISPLAY : d.getUse().getCode());
      designation.setName(d.getValue());
      designation.setLanguage(d.getLanguage());
      designation.setCaseSignificance(caseSignificance);
      designation.setDesignationKind("text");
      designation.setStatus("active");
      return designation;
    }).toList());

    return designations;
  }

  private static List<EntityPropertyValue> mapPropertyValues(List<Property> propertyValues) {
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
      value.setEntityProperty(v.getCode());
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
