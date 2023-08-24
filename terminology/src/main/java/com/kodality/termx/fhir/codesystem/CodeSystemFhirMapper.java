package com.kodality.termx.fhir.codesystem;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.fhir.BaseFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptProperty;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemProperty;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.MarkdownType;
import org.hl7.fhir.r5.model.StringType;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Slf4j
@Context
public class CodeSystemFhirMapper extends BaseFhirMapper {
  private static Optional<String> termxWebUrl;
  public CodeSystemFhirMapper(@Value("${termx.web-url}") Optional<String> termxWebUrl) {
    CodeSystemFhirMapper.termxWebUrl = termxWebUrl;
  }

  public static String toFhirId(CodeSystem cs, CodeSystemVersion csv) {
    return cs.getId() + "@" + csv.getVersion();
  }

  public static String toFhirJson(CodeSystem cs, CodeSystemVersion csv, List<Provenance> provenances) {
    return FhirMapper.toJson(toFhir(cs, csv, provenances));
  }

  public static com.kodality.zmei.fhir.resource.terminology.CodeSystem toFhir(CodeSystem codeSystem, CodeSystemVersion version, List<Provenance> provenances) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem = new com.kodality.zmei.fhir.resource.terminology.CodeSystem();
    termxWebUrl.ifPresent(url -> fhirCodeSystem.addExtension(new Extension("http://hl7.org/fhir/tools/StructureDefinition/web-source")
        .setValueUrl(url + "/fhir/CodeSystem/" + codeSystem.getId())));
    fhirCodeSystem.setId(toFhirId(codeSystem, version));
    fhirCodeSystem.setUrl(codeSystem.getUri());
    fhirCodeSystem.setPublisher(codeSystem.getPublisher());
    fhirCodeSystem.setName(codeSystem.getName());
    fhirCodeSystem.setTitle(toFhirName(codeSystem.getTitle(), version.getPreferredLanguage()));
    fhirCodeSystem.setDescription(toFhirName(codeSystem.getDescription(), version.getPreferredLanguage()));
    fhirCodeSystem.setPurpose(toFhirName(codeSystem.getPurpose(), version.getPreferredLanguage()));
    fhirCodeSystem.setHierarchyMeaning(codeSystem.getHierarchyMeaning());
    fhirCodeSystem.setText(codeSystem.getNarrative() == null ? null : new Narrative().setDiv(codeSystem.getNarrative()));
    fhirCodeSystem.setExperimental(codeSystem.getExperimental());
    fhirCodeSystem.setIdentifier(toFhirIdentifiers(codeSystem.getIdentifiers()));
    fhirCodeSystem.setContact(toFhirContacts(codeSystem.getContacts()));
    fhirCodeSystem.setContent(codeSystem.getContent());
    fhirCodeSystem.setCaseSensitive(
        codeSystem.getCaseSensitive() != null && !CaseSignificance.entire_term_case_insensitive.equals(codeSystem.getCaseSensitive()));
    fhirCodeSystem.setExperimental(codeSystem.getExperimental() != null && codeSystem.getExperimental());
    fhirCodeSystem.setLastReviewDate(Optional.ofNullable(provenances).flatMap(list -> list.stream().filter(p -> "reviewed".equals(p.getActivity()))
        .max(Comparator.comparing(Provenance::getDate)).map(p -> p.getDate().toLocalDate())).orElse(null));
    fhirCodeSystem.setApprovalDate(Optional.ofNullable(provenances).flatMap(list -> list.stream().filter(p -> "approved".equals(p.getActivity()))
        .max(Comparator.comparing(Provenance::getDate)).map(p -> p.getDate().toLocalDate())).orElse(null));
    fhirCodeSystem.setCopyright(codeSystem.getCopyright() != null ? codeSystem.getCopyright().getHolder() : null);
    fhirCodeSystem.setCopyrightLabel(codeSystem.getCopyright() != null ? codeSystem.getCopyright().getStatement() : null);
    fhirCodeSystem.setJurisdiction(codeSystem.getCopyright() != null && codeSystem.getCopyright().getJurisdiction() != null  ? List.of(new CodeableConcept().setText(codeSystem.getCopyright().getJurisdiction())) : null);

    fhirCodeSystem.setVersion(version.getVersion());
    fhirCodeSystem.setLanguage(version.getPreferredLanguage());
    fhirCodeSystem.setVersionAlgorithmString(version.getAlgorithm());
    fhirCodeSystem.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirCodeSystem.setStatus(version.getStatus());
    fhirCodeSystem.setProperty(toFhirCodeSystemProperty(codeSystem.getProperties(), version.getPreferredLanguage()));

    List<Pair<CodeSystemAssociation, CodeSystemEntityVersion>> entitiesWithAssociations =
        version.getEntities().stream().filter(e -> e.getAssociations() != null).flatMap(e -> e.getAssociations().stream().map(a -> Pair.of(a, e))).toList();
    Map<Long, List<CodeSystemEntityVersion>> entities =
        entitiesWithAssociations.stream().collect(Collectors.groupingBy(e -> e.getKey().getTargetId(), mapping(Pair::getValue, toList())));

    fhirCodeSystem.setConcept(version.getEntities().stream()
        .filter(e -> CollectionUtils.isEmpty(e.getAssociations()))
        .map(e -> toFhir(e, codeSystem, version, entities))
        .sorted(Comparator.comparing(CodeSystemConcept::getCode))
        .collect(Collectors.toList()));
    return fhirCodeSystem;
  }

  private static CodeSystemConcept toFhir(CodeSystemEntityVersion e, CodeSystem codeSystem, CodeSystemVersion version,
                                          Map<Long, List<CodeSystemEntityVersion>> entities) {
    CodeSystemConcept concept = new CodeSystemConcept();
    concept.setCode(e.getCode());
    setDesignations(concept, e.getDesignations(), version.getPreferredLanguage());
    concept.setProperty(toFhirConceptProperties(e.getPropertyValues(), codeSystem.getProperties()));
    concept.setConcept(getChildConcepts(entities, e.getId(), codeSystem, version));
    return concept;
  }

  private static void setDesignations(CodeSystemConcept concept, List<Designation> designations, String preferredLanguage) {
    if (designations == null) {
      return;
    }
    Designation display = designations.stream()
        .filter(d -> d.getDesignationType().equals("display") && (preferredLanguage == null || preferredLanguage.equals(d.getLanguage())))
        .findFirst().orElse(null);
    Designation definition = designations.stream()
        .filter(d -> d.getDesignationType().equals("definition") && (preferredLanguage == null || preferredLanguage.equals(d.getLanguage())))
        .findFirst().orElse(null);
    concept.setDisplay(display != null ? display.getName() : null);
    concept.setDefinition(definition != null ? definition.getName() : null);
    concept.setDesignation(toFhirDesignations(designations.stream()
        .filter(d -> (display == null || !display.getId().equals(d.getId()) && (definition == null || !definition.getId().equals(d.getId())))).toList()));
  }

  private static List<CodeSystemConceptDesignation> toFhirDesignations(List<Designation> designations) {
    if (CollectionUtils.isEmpty(designations)) {
      return null;
    }
    return designations.stream().map(d -> new CodeSystemConceptDesignation()
            .setLanguage(d.getLanguage())
            .setValue(d.getName())
            .setUse(new Coding(d.getDesignationType())))
        .sorted(Comparator.comparing(d -> d.getLanguage() == null ? "" : d.getLanguage()))
        .sorted(Comparator.comparing(d -> d.getUse().getCode()))
        .toList();
  }

  private static List<CodeSystemProperty> toFhirCodeSystemProperty(List<EntityProperty> entityProperties, String lang) {
    if (CollectionUtils.isEmpty(entityProperties)) {
      return List.of();
    }
    return entityProperties.stream().map(p ->
        new CodeSystemProperty()
            .setCode(p.getName())
            .setType(p.getType())
            .setUri(p.getUri())
            .setDescription(toFhirName(p.getDescription(), lang))
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

  private static List<CodeSystemConcept> getChildConcepts(Map<Long, List<CodeSystemEntityVersion>> entities, Long targetId, CodeSystem codeSystem,
                                                          CodeSystemVersion version) {
    List<CodeSystemConcept> result = entities.getOrDefault(targetId, List.of()).stream().map(e -> toFhir(e, codeSystem, version, entities)).collect(Collectors.toList());
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
