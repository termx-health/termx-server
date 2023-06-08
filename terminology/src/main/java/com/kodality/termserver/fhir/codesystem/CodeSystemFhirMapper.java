package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termserver.fhir.BaseFhirMapper;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Narrative;
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
import java.util.stream.Collectors;
import javax.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;

@Singleton
public class CodeSystemFhirMapper extends BaseFhirMapper {

  public String toFhirJson(CodeSystem cs, CodeSystemVersion csv) {
    return FhirMapper.toJson(toFhir(cs, csv));
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
    return designations.stream().filter(d -> d.getDesignationTypeId().equals(property.getId())).min(Comparator.comparing(Designation::getLanguage))
        .map(Designation::getName).orElse(null);
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
        switch (entityProperty.getType()) {
          case EntityPropertyType.code -> fhirProperty.setValueCode((String) value);
          case EntityPropertyType.string -> fhirProperty.setValueString((String) value);
          case EntityPropertyType.bool -> fhirProperty.setValueBoolean((Boolean) value);
          case EntityPropertyType.decimal -> fhirProperty.setValueDecimal(new BigDecimal(String.valueOf(value)));
          case EntityPropertyType.integer -> fhirProperty.setValueInteger(Integer.valueOf(String.valueOf(value)));
          case EntityPropertyType.dateTime -> {
            if (value instanceof OffsetDateTime) {
              fhirProperty.setValueDateTime((OffsetDateTime) value);
            } else {
              fhirProperty.setValueDateTime(DateUtil.parseOffsetDateTime((String) value));
            }
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


  public CodeSystemQueryParams fromFhir(SearchCriterion fhir) {
    CodeSystemQueryParams params = new CodeSystemQueryParams();
    getSimpleParams(fhir).forEach((k, v) -> {
      switch (k) {
        case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
        case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
        case "_id" -> params.setIds(v);
        case "system" -> params.setUri(v);
        case "url" -> params.setUri(v);
        case "version" -> params.setVersionVersion(v);
        case "title" -> params.setNameContains(v);
        case "name" -> params.setNameContains(v);
        case "status" -> params.setVersionStatus(v);
        case "publisher" -> params.setVersionSource(v);
        case "description" -> params.setDescriptionContains(v);
        case "content-mode" -> params.setContent(v);
        case "code" -> params.setConceptCode(v);
        default -> throw new ApiClientException("Search by '" + k + "' not supported");
      }
    });
    params.setVersionsDecorated(true);
    params.setPropertiesDecorated(true);
    return params;
  }
}
