package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.ContactDetail;
import com.kodality.termserver.ts.ContactDetail.Telecom;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptProperty;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemFhirImportMapper {
  private static final String DISPLAY = "display";
  private static final String DEFINITION = "definition";

  public static CodeSystem mapCodeSystem(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(CodeSystemFhirMapper.parseCompositeId(fhirCodeSystem.getId())[0]);
    codeSystem.setUri(fhirCodeSystem.getUrl());
    codeSystem.setPublisher(fhirCodeSystem.getPublisher());
    codeSystem.setTitle(toLocalizedName(fhirCodeSystem.getTitle()));
    codeSystem.setName(toLocalizedName(fhirCodeSystem.getName()));
    codeSystem.setDescription(toLocalizedName(fhirCodeSystem.getDescription()));
    codeSystem.setPurpose(toLocalizedName(fhirCodeSystem.getPurpose()));
    codeSystem.setHierarchyMeaning(fhirCodeSystem.getHierarchyMeaning());
    codeSystem.setNarrative(fhirCodeSystem.getText() == null ? null : fhirCodeSystem.getText().getDiv());
    codeSystem.setExperimental(fhirCodeSystem.getExperimental());
    codeSystem.setIdentifiers(mapIdentifiers(fhirCodeSystem.getIdentifier()));
    codeSystem.setContacts(mapContacts(fhirCodeSystem.getContact()));
    codeSystem.setContent(fhirCodeSystem.getContent());
    codeSystem.setCaseSensitive(fhirCodeSystem.getCaseSensitive() != null && fhirCodeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive :
        CaseSignificance.entire_term_case_insensitive);
    //TODO: copyright

    codeSystem.setVersions(mapVersion(fhirCodeSystem));
    codeSystem.setConcepts(mapConcepts(fhirCodeSystem.getConcept(), fhirCodeSystem, null));
    codeSystem.setProperties(mapProperties(fhirCodeSystem));
    return codeSystem;
  }

  private static LocalizedName toLocalizedName(String name) {
    if (name == null) {
      return null;
    }
    return new LocalizedName(Map.of(Language.en, name));
  }

  private static List<Identifier> mapIdentifiers(List<com.kodality.zmei.fhir.datatypes.Identifier> identifiers) {
    if (identifiers == null) {
      return null;
    }
    return identifiers.stream().map(i -> new Identifier(i.getSystem(), i.getValue())).collect(Collectors.toList());
  }

  private static List<ContactDetail> mapContacts(List<com.kodality.zmei.fhir.datatypes.ContactDetail> details) {
    if (CollectionUtils.isEmpty(details)) {
      return new ArrayList<>();
    }
    return details.stream().map(c -> {
      ContactDetail contact = new ContactDetail();
      contact.setName(c.getName());
      contact.setTelecoms(c.getTelecom() == null ? null : c.getTelecom().stream().map(t ->
          new Telecom().setSystem(t.getSystem()).setUse(t.getUse()).setValue(t.getValue())
      ).collect(Collectors.toList()));
      return contact;
    }).toList();
  }

  private static List<CodeSystemVersion> mapVersion(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(fhirCodeSystem.getId());
    version.setVersion(fhirCodeSystem.getVersion() == null ? "1.0.0" : fhirCodeSystem.getVersion());
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

    List<EntityProperty> properties = fhirCodeSystem.getProperty().stream().map(p -> {
      EntityProperty property = new EntityProperty();
      property.setName(p.getCode());
      property.setDescription(p.getDescription());
      property.setType(p.getType());
      property.setStatus(PublicationStatus.active);
      return property;
    }).collect(Collectors.toList());
    properties.addAll(defaultProperties);

    if (fhirCodeSystem.getConcept() != null) {
      List<EntityProperty> designationProperties = fhirCodeSystem.getConcept().stream()
          .filter(c -> c.getDesignation() != null)
          .flatMap(c -> c.getDesignation().stream())
          .filter(d -> d.getUse() != null && d.getUse().getCode() != null)
          .map(d -> new EntityProperty().setName(d.getUse().getCode()).setType(EntityPropertyType.string).setStatus(PublicationStatus.active)).toList();
      properties.addAll(designationProperties);
    }
    return properties.stream().collect(Collectors.toMap(EntityProperty::getName, p -> p, (p, q) -> p)).values().stream().toList();
  }

  private static List<Concept> mapConcepts(List<CodeSystemConcept> fhirConcepts,
                                           com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem, CodeSystemConcept parent) {
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

  private static List<CodeSystemEntityVersion> mapConceptVersion(CodeSystemConcept c,
                                                                 com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem,
                                                                 CodeSystemConcept parent) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(c.getCode());
    version.setCodeSystem(codeSystem.getId());
    version.setDesignations(mapDesignations(c, codeSystem));
    version.setPropertyValues(mapPropertyValues(c.getProperty()));
    version.setAssociations(mapAssociations(parent, codeSystem));
    version.setStatus(mapStatus(c.getProperty()));
    return List.of(version);
  }

  private static String mapStatus(List<CodeSystemConceptProperty> propertyValues) {
    if (propertyValues == null) {
      return null;
    }
    return propertyValues.stream().filter(pv -> "status".equals(pv.getCode())).findFirst().map(CodeSystemConceptProperty::getValueCode).orElse(null);
  }

  private static List<Designation> mapDesignations(CodeSystemConcept c,
                                                   com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    String caseSignificance = codeSystem.getCaseSensitive() != null && codeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive : CaseSignificance.entire_term_case_insensitive;

    if (c.getDesignation() == null) {
      c.setDesignation(new ArrayList<>());
    }
    List<Designation> designations = c.getDesignation().stream().filter(d -> d.getLanguage() != null).map(d -> {
      Designation designation = new Designation();
      designation.setDesignationType(d.getUse() == null ? DISPLAY : d.getUse().getCode());
      designation.setName(d.getValue());
      designation.setLanguage(d.getLanguage());
      designation.setCaseSignificance(caseSignificance);
      designation.setDesignationKind("text");
      designation.setStatus("active");
      return designation;
    }).collect(Collectors.toList());

    Designation display = new Designation();
    display.setDesignationType(DISPLAY);
    display.setName(c.getDisplay());
    display.setPreferred(true);
    display.setLanguage(Language.en);
    display.setCaseSignificance(caseSignificance);
    display.setDesignationKind("text");
    display.setStatus("active");
    if (designations.stream().noneMatch(d -> isSameDesignation(d, display))) {
      designations.add(display);
    }

    if (c.getDefinition() != null) {
      Designation definition = new Designation();
      definition.setDesignationType(DEFINITION);
      definition.setName(c.getDefinition());
      definition.setLanguage(Language.en);
      definition.setCaseSignificance(caseSignificance);
      definition.setDesignationKind("text");
      definition.setStatus("active");
      if (designations.stream().noneMatch(d -> isSameDesignation(d, definition))) {
        designations.add(definition);
      }
    }
    return designations;
  }

  private static boolean isSameDesignation(Designation d1, Designation d2) {
    return d1.getDesignationType().equals(d2.getDesignationType()) && d1.getName().equals(d2.getName());
  }

  private static List<EntityPropertyValue> mapPropertyValues(List<CodeSystemConceptProperty> propertyValues) {
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

  private static List<CodeSystemAssociation> mapAssociations(CodeSystemConcept parent,
                                                             com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    if (parent == null) {
      return new ArrayList<>();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(codeSystem.getHierarchyMeaning() == null ? "is-a" : codeSystem.getHierarchyMeaning());
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(parent.getCode());
    association.setCodeSystem(codeSystem.getId());
    return List.of(association);
  }
}
