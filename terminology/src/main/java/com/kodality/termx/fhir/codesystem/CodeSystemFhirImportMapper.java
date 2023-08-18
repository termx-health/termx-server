package com.kodality.termx.fhir.codesystem;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.ContactDetail.Telecom;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystem.CodeSystemCopyright;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptProperty;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    codeSystem.setName(fhirCodeSystem.getName());
    codeSystem.setTitle(toLocalizedName(fhirCodeSystem.getTitle()));
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
    codeSystem.setCopyright(new CodeSystemCopyright().setHolder(fhirCodeSystem.getCopyright()).setStatement(fhirCodeSystem.getCopyrightLabel()));

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
    version.setPreferredLanguage(fhirCodeSystem.getLanguage() == null ? Language.en : fhirCodeSystem.getLanguage());
    version.setSupportedLanguages(Optional.ofNullable(fhirCodeSystem.getConcept()).orElse(new ArrayList<>()).stream()
        .filter(c -> c.getDesignation() != null)
        .flatMap(c -> c.getDesignation().stream().map(CodeSystemConceptDesignation::getLanguage)).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
    if (!version.getSupportedLanguages().contains(version.getPreferredLanguage())) {
      version.getSupportedLanguages().add(version.getPreferredLanguage());
    }
    version.setStatus(PublicationStatus.draft);
    version.setAlgorithm(fhirCodeSystem.getVersionAlgorithmString());
    version.setReleaseDate(fhirCodeSystem.getDate() == null ? LocalDate.now() : LocalDate.from(fhirCodeSystem.getDate()));
    return List.of(version);
  }

  private static List<EntityProperty> mapProperties(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    List<EntityProperty> defaultProperties = new ArrayList<>();

    EntityProperty display = new EntityProperty();
    display.setName(DISPLAY);
    display.setType(EntityPropertyType.string);
    display.setKind(EntityPropertyKind.designation);
    display.setStatus(PublicationStatus.active);

    EntityProperty definition = new EntityProperty();
    definition.setName(DEFINITION);
    definition.setType(EntityPropertyType.string);
    definition.setKind(EntityPropertyKind.designation);
    definition.setStatus(PublicationStatus.active);

    defaultProperties.add(display);
    defaultProperties.add(definition);
    if (fhirCodeSystem.getProperty() == null) {
      return defaultProperties;
    }

    List<EntityProperty> properties = fhirCodeSystem.getProperty().stream().map(p -> {
      EntityProperty property = new EntityProperty();
      property.setName(p.getCode());
      property.setUri(p.getUri());
      property.setDescription(toLocalizedName(p.getDescription()));
      property.setType(p.getType());
      property.setKind(EntityPropertyKind.property);
      property.setStatus(PublicationStatus.active);
      return property;
    }).collect(Collectors.toList());
    properties.addAll(defaultProperties);

    if (fhirCodeSystem.getConcept() != null) {
      List<EntityProperty> designationProperties = fhirCodeSystem.getConcept().stream()
          .filter(c -> c.getDesignation() != null)
          .flatMap(c -> c.getDesignation().stream())
          .filter(d -> d.getUse() != null && d.getUse().getCode() != null)
          .map(d -> {
            EntityProperty ep = new EntityProperty();
            ep.setName(d.getUse().getCode());
            ep.setType(EntityPropertyType.string);
            ep.setKind(EntityPropertyKind.designation);
            ep.setStatus(PublicationStatus.active);
            return ep;
          }).toList();
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
    List<Designation> designations = c.getDesignation().stream().map(d -> {
      Designation designation = new Designation();
      designation.setDesignationType(d.getUse() == null ? DISPLAY : d.getUse().getCode());
      designation.setName(d.getValue());
      designation.setLanguage(d.getLanguage() == null ? Language.en : d.getLanguage());
      designation.setCaseSignificance(caseSignificance);
      designation.setDesignationKind("text");
      designation.setStatus("active");
      return designation;
    }).collect(Collectors.toList());

    Designation display = new Designation();
    display.setDesignationType(DISPLAY);
    display.setName(c.getDisplay());
    display.setPreferred(true);
    display.setLanguage(codeSystem.getLanguage() == null ? Language.en : codeSystem.getLanguage());
    display.setCaseSignificance(caseSignificance);
    display.setDesignationKind("text");
    display.setStatus("active");
    if (display.getName() != null && designations.stream().noneMatch(d -> isSameDesignation(d, display))) {
      designations.add(display);
    }

    if (c.getDefinition() != null) {
      Designation definition = new Designation();
      definition.setDesignationType(DEFINITION);
      definition.setName(c.getDefinition());
      definition.setLanguage(codeSystem.getLanguage() == null ? Language.en : codeSystem.getLanguage());
      definition.setCaseSignificance(caseSignificance);
      definition.setDesignationKind("text");
      definition.setStatus("active");
      if (definition.getName() != null && designations.stream().noneMatch(d -> isSameDesignation(d, definition))) {
        designations.add(definition);
      }
    }
    return designations;
  }

  private static boolean isSameDesignation(Designation d1, Designation d2) {
    return d1.getDesignationType().equals(d2.getDesignationType()) && d1.getName().equals(d2.getName()) && d1.getLanguage().equals(d2.getLanguage());
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
