package com.kodality.termx.fhir.codesystem;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.fhir.BaseFhirMapper;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptProperty;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class CodeSystemFhirMapper extends BaseFhirMapper {

  public static String toFhirId(CodeSystem cs, CodeSystemVersion csv) {
    return cs.getId() + "@" + csv.getVersion();
  }

  public static String toFhirJson(CodeSystem cs, CodeSystemVersion csv) {
    return FhirMapper.toJson(toFhir(cs, csv));
  }

  public static com.kodality.zmei.fhir.resource.terminology.CodeSystem toFhir(CodeSystem codeSystem, CodeSystemVersion version) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem = new com.kodality.zmei.fhir.resource.terminology.CodeSystem();
    fhirCodeSystem.setId(toFhirId(codeSystem, version));
    fhirCodeSystem.setUrl(codeSystem.getUri());
    fhirCodeSystem.setPublisher(codeSystem.getPublisher());
    fhirCodeSystem.setTitle(toFhirName(codeSystem.getTitle()));
    fhirCodeSystem.setName(toFhirName(codeSystem.getName()));
    fhirCodeSystem.setDescription(toFhirName(codeSystem.getDescription()));
    fhirCodeSystem.setPurpose(toFhirName(codeSystem.getPurpose()));
    fhirCodeSystem.setHierarchyMeaning(codeSystem.getHierarchyMeaning());
    fhirCodeSystem.setText(codeSystem.getNarrative() == null ? null : new Narrative().setDiv(codeSystem.getNarrative()));
    fhirCodeSystem.setExperimental(codeSystem.getExperimental());
    fhirCodeSystem.setIdentifier(toFhirIdentifiers(codeSystem.getIdentifiers()));
    fhirCodeSystem.setContact(toFhirContacts(codeSystem.getContacts()));
    fhirCodeSystem.setContent(codeSystem.getContent());
    fhirCodeSystem.setCaseSensitive(codeSystem.getCaseSensitive() != null && !CaseSignificance.entire_term_case_insensitive.equals(codeSystem.getCaseSensitive()));

    fhirCodeSystem.setVersion(version.getVersion());
    fhirCodeSystem.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirCodeSystem.setStatus(version.getStatus());
    fhirCodeSystem.setProperty(toFhirCodeSystemProperty(codeSystem.getProperties()));
    fhirCodeSystem.setConcept(version.getEntities().stream()
        .filter(e -> CollectionUtils.isEmpty(e.getAssociations()))
        .map(e -> toFhir(e, codeSystem, version.getEntities()))
        .sorted(Comparator.comparing(CodeSystemConcept::getCode))
        .collect(Collectors.toList()));

    return fhirCodeSystem;
  }

  private static CodeSystemConcept toFhir(CodeSystemEntityVersion e, CodeSystem codeSystem, List<CodeSystemEntityVersion> entities) {
    CodeSystemConcept concept = new CodeSystemConcept();
    concept.setCode(e.getCode());
    concept.setDisplay(findDesignation(e.getDesignations(), codeSystem.getProperties(), "display"));
    concept.setDefinition(findDesignation(e.getDesignations(), codeSystem.getProperties(), "definition"));
    concept.setDesignation(toFhirDesignations(e.getDesignations()));
    concept.setProperty(toFhirConceptProperties(e.getPropertyValues(), codeSystem.getProperties()));
    concept.setConcept(getChildConcepts(entities, e.getId(), codeSystem));
    return concept;
  }

  private static List<CodeSystemConceptDesignation> toFhirDesignations(List<Designation> designations) {
    if (CollectionUtils.isEmpty(designations)) {
      return null;
    }
    return designations.stream().map(d -> new CodeSystemConceptDesignation()
            .setLanguage(d.getLanguage())
            .setValue(d.getName())
            .setUse(new Coding(d.getDesignationType())))
        .sorted(Comparator.comparing(CodeSystemConceptDesignation::getLanguage))
        .sorted(Comparator.comparing(d -> d.getUse().getCode()))
        .toList();
  }

  private static String findDesignation(List<Designation> designations, List<EntityProperty> properties, String propertyName) {
    EntityProperty property = properties.stream().filter(p -> p.getName().equals(propertyName)).findFirst().orElse(null);
    if (property == null || designations == null) {
      return null;
    }
    return designations.stream()
        .filter(d -> d.getDesignationTypeId().equals(property.getId()))
        .min(Comparator.comparing(Designation::getLanguage))
        .map(Designation::getName).orElse(null);
  }

  private static List<CodeSystemProperty> toFhirCodeSystemProperty(List<EntityProperty> entityProperties) {
    if (CollectionUtils.isEmpty(entityProperties)) {
      return List.of();
    }
    return entityProperties.stream().map(p ->
        new CodeSystemProperty()
            .setCode(p.getName())
            .setType(p.getType())
            .setDescription(p.getDescription())
    ).sorted(Comparator.comparing(CodeSystemProperty::getCode)).toList();
  }

  private static List<CodeSystemConceptProperty> toFhirConceptProperties(List<EntityPropertyValue> propertyValues, List<EntityProperty> properties) {
    if (CollectionUtils.isEmpty(propertyValues)) {
      return null;
    }
    Map<Long, EntityProperty> entityProperties = properties.stream().collect(Collectors.toMap(ep -> ep.getId(), ep -> ep));
    return propertyValues.stream().map(pv -> {
      EntityProperty entityProperty = entityProperties.get(pv.getEntityPropertyId());
      CodeSystemConceptProperty fhir = new CodeSystemConceptProperty();
      fhir.setCode(entityProperty.getName());
      switch (entityProperty.getType()) {
        case EntityPropertyType.code -> fhir.setValueCode((String) pv.getValue());
        case EntityPropertyType.string -> fhir.setValueString((String) pv.getValue());
        case EntityPropertyType.bool -> fhir.setValueBoolean((Boolean) pv.getValue());
        case EntityPropertyType.decimal -> fhir.setValueDecimal(new BigDecimal(String.valueOf(pv.getValue())));
        case EntityPropertyType.integer -> fhir.setValueInteger(Integer.valueOf(String.valueOf(pv.getValue())));
        case EntityPropertyType.coding -> {
          Concept concept = JsonUtil.getObjectMapper().convertValue(pv.getValue(), Concept.class);
          fhir.setValueCoding(new Coding(concept.getCodeSystem(), concept.getCode()));
        }
        case EntityPropertyType.dateTime -> {
          if (pv.getValue() instanceof OffsetDateTime) {
            fhir.setValueDateTime((OffsetDateTime) pv.getValue());
          } else {
            fhir.setValueDateTime(DateUtil.parseOffsetDateTime((String) pv.getValue()));
          }
        }
      }
      return fhir;
    }).sorted(Comparator.comparing(CodeSystemConceptProperty::getCode)).toList();
  }

  private static List<CodeSystemConcept> getChildConcepts(List<CodeSystemEntityVersion> entities, Long targetId, CodeSystem codeSystem) {
    List<CodeSystemConcept> result =
        entities.stream().filter(e -> e.getAssociations() != null)
            .filter(e -> e.getAssociations().stream().anyMatch(a -> a.getTargetId().equals(targetId)))
            .map(e -> toFhir(e, codeSystem, entities)).collect(Collectors.toList());
    return CollectionUtils.isEmpty(result) ? null : result.stream().sorted(Comparator.comparing(CodeSystemConcept::getCode)).toList();
  }

  public static CodeSystemQueryParams fromFhir(SearchCriterion fhir) {
    CodeSystemQueryParams params = new CodeSystemQueryParams();
    getSimpleParams(fhir).forEach((k, v) -> {
      switch (k) {
        case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
        case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
        case "_id" -> params.setIds(v);
        case "system", "url" -> params.setUri(v);
        case "version" -> params.setVersionVersion(v);
        case "title", "name" -> params.setNameContains(v);
        case "status" -> params.setVersionStatus(v);
        case "publisher" -> params.setPublisher(v);
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
