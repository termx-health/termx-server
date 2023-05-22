package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptProperty;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;

@Singleton
public class CodeSystemFhirMapper {

  public static String extractVersion(CodeSystem cs) {
    if (cs.getVersions() == null || cs.getVersions().size() == 0) {
      return null;
    }
    return cs.getVersions().get(0).getVersion();
  }

  public static String extractDisplay(CodeSystem cs) {
    return getDesignations(cs).filter(Designation::isPreferred).findFirst().map(Designation::getName).orElse(null);
  }

  public static List<ParametersParameter> extractDesignations(CodeSystem cs) {
    return getDesignations(cs)
        .filter(d -> !d.isPreferred())
        .map(d -> {
          List<ParametersParameter> part = new ArrayList<>();
          part.add(new ParametersParameter().setName("value").setValueString(d.getName()));
          part.add(new ParametersParameter().setName("language").setValueCode(d.getLanguage()));
          return new ParametersParameter().setName("designation").setPart(part);
        }).collect(Collectors.toList());
  }

  public static List<ParametersParameter> extractProperties(CodeSystem cs, List<String> properties) {
    if (!hasProperties(cs)) {
      return new ArrayList<>();
    }
    return cs.getConcepts().get(0).getVersions().get(0).getPropertyValues().stream()
        .map(p -> {
          List<ParametersParameter> part = new ArrayList<>();
          Optional<EntityProperty> property = cs.getProperties().stream()
              .filter(pr -> (CollectionUtils.isEmpty(properties) || properties.contains(pr.getName())) && pr.getId().equals(p.getEntityPropertyId()))
              .findFirst();
          if (property.isPresent()) {
            part.add(new ParametersParameter().setName("code").setValueCode(property.get().getName()));
            part.add(new ParametersParameter().setName("description").setValueCode(property.get().getDescription()));
            if (property.get().getType().equals("code")) {
              part.add(new ParametersParameter().setName("value").setValueCode((String) p.getValue()));
            }
            if (property.get().getType().equals("string")) {
              part.add(new ParametersParameter().setName("value").setValueString((String) p.getValue()));
            }
            if (property.get().getType().equals("boolean")) {
              part.add(new ParametersParameter().setName("value").setValueBoolean((Boolean) p.getValue()));
            }
            if (property.get().getType().equals("dateTime")) {
              part.add(new ParametersParameter().setName("value").setValueDateTime((OffsetDateTime) p.getValue()));
            }
            if (property.get().getType().equals("decimal")) {
              part.add(new ParametersParameter().setName("value").setValueDecimal((BigDecimal) p.getValue()));
            }
            //TODO value type coding
          }
          return new ParametersParameter().setName("property").setPart(part);
        }).collect(Collectors.toList());
  }

  public static String extractDisplay(Concept c) {
    return getDesignations(c).filter(Designation::isPreferred).findFirst().map(Designation::getName).orElse(null);
  }

  private static boolean hasProperties(CodeSystem cs) {
    return !(cs.getConcepts() == null || cs.getConcepts().size() == 0 ||
        cs.getConcepts().get(0).getVersions() == null || cs.getConcepts().get(0).getVersions().size() == 0 ||
        cs.getConcepts().get(0).getVersions().get(0).getPropertyValues() == null);
  }

  private static Stream<Designation> getDesignations(CodeSystem cs) {
    return CollectionUtils.isEmpty(cs.getConcepts()) ? Stream.of() : getDesignations(cs.getConcepts().get(0));
  }

  private static Stream<Designation> getDesignations(Concept c) {
    return CollectionUtils.isEmpty(c.getVersions()) || c.getVersions().get(0).getDesignations() == null ? Stream.of() :
        c.getVersions().get(0).getDesignations().stream();
  }

  public com.kodality.zmei.fhir.resource.terminology.CodeSystem toFhir(CodeSystem codeSystem, CodeSystemVersion version) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem = new com.kodality.zmei.fhir.resource.terminology.CodeSystem();
    fhirCodeSystem.setId(codeSystem.getId());
    fhirCodeSystem.setUrl(codeSystem.getUri());
    //TODO identifiers from naming-system
    fhirCodeSystem.setName(codeSystem.getNames().getOrDefault(Language.en, codeSystem.getNames().values().stream().findFirst().orElse(null)));
    fhirCodeSystem.setContent(codeSystem.getContent());
    fhirCodeSystem.setContact(codeSystem.getContacts() == null ? null : codeSystem.getContacts().stream()
        .map(c -> new ContactDetail().setName(c.getName()).setTelecom(c.getTelecoms() == null ? null : c.getTelecoms().stream().map(t ->
            new ContactPoint().setSystem(t.getSystem()).setValue(t.getValue()).setUse(t.getUse())).collect(Collectors.toList())))
        .collect(Collectors.toList()));
    fhirCodeSystem.setText(codeSystem.getNarrative() == null ? null : new Narrative().setDiv(codeSystem.getNarrative()));
    fhirCodeSystem.setDescription(codeSystem.getDescription());
    fhirCodeSystem.setCaseSensitive(
        codeSystem.getCaseSensitive() != null && !CaseSignificance.entire_term_case_insensitive.equals(codeSystem.getCaseSensitive()));

    if (version != null) {
      fhirCodeSystem.setVersion(version.getVersion());
      fhirCodeSystem.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
      fhirCodeSystem.setStatus(version.getStatus());
      fhirCodeSystem.setPublisher(version.getSource());
      fhirCodeSystem.setConcept(version.getEntities().stream()
          .filter(e -> CollectionUtils.isEmpty(e.getAssociations()))
          .map(e -> toFhir(e, codeSystem, version.getEntities(), fhirCodeSystem))
          .sorted(Comparator.comparing(CodeSystemConcept::getCode))
          .collect(Collectors.toList()));
    }
    return fhirCodeSystem;
  }

  private CodeSystemConcept toFhir(CodeSystemEntityVersion e, CodeSystem codeSystem,
                                   List<CodeSystemEntityVersion> entities,
                                   com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystemConcept concept = new CodeSystemConcept();
    concept.setCode(e.getCode());
    concept.setDisplay(findDesignation(e.getDesignations(), codeSystem.getProperties(), "display"));
    concept.setDefinition(findDesignation(e.getDesignations(), codeSystem.getProperties(), "definition"));
    concept.setDesignation(getDesignations(e.getDesignations()));
    concept.setProperty(getProperties(e.getPropertyValues(), codeSystem.getProperties(), fhirCodeSystem));
    concept.setConcept(getChildConcepts(entities, e.getId(), codeSystem, fhirCodeSystem));
    return concept;
  }

  private List<CodeSystemConceptDesignation> getDesignations(List<Designation> designations) {
    if (designations == null) {
      return List.of();
    }
    List<CodeSystemConceptDesignation> result = designations.stream().map(d -> {
          CodeSystemConceptDesignation fhirDesignation = new CodeSystemConceptDesignation();
          fhirDesignation.setLanguage(d.getLanguage());
          fhirDesignation.setValue(d.getName());
          fhirDesignation.setUse(new Coding(d.getDesignationType()));
          return fhirDesignation;
        }).sorted(Comparator.comparing(CodeSystemConceptDesignation::getLanguage)).sorted(Comparator.comparing(d -> d.getUse().getCode()))
        .collect(Collectors.toList());
    return CollectionUtils.isEmpty(result) ? null : result;
  }

  private String findDesignation(List<Designation> designations, List<EntityProperty> properties, String propertyName) {
    EntityProperty property = properties.stream().filter(p -> p.getName().equals(propertyName)).findFirst().orElse(null);
    if (property == null) {
      return null;
    }
    return designations.stream().filter(d -> d.getDesignationTypeId().equals(property.getId())).min(Comparator.comparing(Designation::getLanguage)).map(Designation::getName).orElse(null);
  }

  private List<CodeSystemConceptProperty> getProperties(List<EntityPropertyValue> propertyValues, List<EntityProperty> properties,
                                                        com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    List<CodeSystemConceptProperty> fhirProperties = new ArrayList<>();
    if (propertyValues == null) {
      return fhirProperties;
    }
    propertyValues.forEach(pv -> {
      EntityProperty entityProperty = properties.stream().filter(p -> p.getId().equals(pv.getEntityPropertyId())).findFirst().orElse(null);
      if (entityProperty != null) {
        addToProperties(fhirCodeSystem, entityProperty);
        CodeSystemConceptProperty fhirProperty = new CodeSystemConceptProperty();
        fhirProperty.setCode(entityProperty.getName());
        Object value = pv.getValue();
        if (entityProperty.getType().equals(EntityPropertyType.coding)) {
          Concept concept = JsonUtil.fromJson(JsonUtil.toJson(value), Concept.class);
          fhirProperty.setValueCoding(new Coding(concept.getCodeSystem(), concept.getCode()));
        }
        if (entityProperty.getType().equals(EntityPropertyType.code)) {
          fhirProperty.setValueCode((String) value);
        } else if (entityProperty.getType().equals(EntityPropertyType.string)) {
          fhirProperty.setValueString((String) value);
        } else if (entityProperty.getType().equals(EntityPropertyType.bool)) {
          fhirProperty.setValueBoolean((Boolean) value);
        } else if (entityProperty.getType().equals(EntityPropertyType.decimal)) {
          fhirProperty.setValueDecimal(new BigDecimal(String.valueOf(value)));
        } else if (entityProperty.getType().equals(EntityPropertyType.integer)) {
          fhirProperty.setValueInteger(Integer.valueOf(String.valueOf(value)));
        } else if (entityProperty.getType().equals(EntityPropertyType.dateTime)) {
          if (value instanceof OffsetDateTime) {
            fhirProperty.setValueDateTime((OffsetDateTime) value);
          } else {
            fhirProperty.setValueDateTime(DateUtil.parseOffsetDateTime((String) value));
          }
        }
        fhirProperties.add(fhirProperty);
      }
    });
    fhirProperties.sort(Comparator.comparing(CodeSystemConceptProperty::getCode));
    return CollectionUtils.isEmpty(fhirProperties) ? null : fhirProperties.stream().sorted(Comparator.comparing(CodeSystemConceptProperty::getCode)).toList();
  }

  private void addToProperties(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem, EntityProperty entityProperty) {
    CodeSystemProperty propertyDescription = new CodeSystemProperty();
    propertyDescription.setCode(entityProperty.getName());
    propertyDescription.setType(entityProperty.getType());
    propertyDescription.setDescription(entityProperty.getDescription());
    if (CollectionUtils.isEmpty(fhirCodeSystem.getProperty())) {
      fhirCodeSystem.setProperty(new ArrayList<>(List.of(propertyDescription)));
      return;
    }

    if (fhirCodeSystem.getProperty().stream().noneMatch(p -> p.getCode().equals(propertyDescription.getCode()))) {
      fhirCodeSystem.getProperty().add(propertyDescription);
      fhirCodeSystem.setProperty(fhirCodeSystem.getProperty().stream().sorted(Comparator.comparing(CodeSystemProperty::getCode)).collect(Collectors.toList()));
    }
  }

  private List<CodeSystemConcept> getChildConcepts(List<CodeSystemEntityVersion> entities,
                                                   Long targetId, CodeSystem codeSystem,
                                                   com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    List<CodeSystemConcept> result =
        entities.stream().filter(e -> e.getAssociations() != null)
            .filter(e -> e.getAssociations().stream().anyMatch(a -> a.getTargetId().equals(targetId)))
            .map(e -> toFhir(e, codeSystem, entities, fhirCodeSystem)).collect(Collectors.toList());
    return CollectionUtils.isEmpty(result) ? null : result.stream().sorted(Comparator.comparing(CodeSystemConcept::getCode)).toList();
  }
}
