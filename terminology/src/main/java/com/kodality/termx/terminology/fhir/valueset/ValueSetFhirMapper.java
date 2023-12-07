package com.kodality.termx.terminology.fhir.valueset;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.Permissions;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetSnapshot;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetCompose;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConcept;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeFilter;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContainsProperty;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

import static java.util.stream.Collectors.toList;

@Context
public class ValueSetFhirMapper extends BaseFhirMapper {
  private static Optional<String> termxWebUrl;
  private static final String concept_definition = "http://hl7.org/fhir/StructureDefinition/valueset-concept-definition";
  private static final String concept_order = "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder";

  public ValueSetFhirMapper(@Value("${termx.web-url}") Optional<String> termxWebUrl) {
    ValueSetFhirMapper.termxWebUrl = termxWebUrl;
  }


  // -------------- TO FHIR --------------

  public static String toFhirId(ValueSet vs, ValueSetVersion vsv) {
    return vs.getId() + BaseFhirMapper.SEPARATOR + vsv.getVersion();
  }

  public static String toFhirJson(ValueSet vs, ValueSetVersion vsv, List<Provenance> provenances) {
    return FhirMapper.toJson(toFhir(vs, vsv, provenances));
  }

  public static com.kodality.zmei.fhir.resource.terminology.ValueSet toFhir(ValueSet valueSet, ValueSetVersion version, List<Provenance> provenances) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet = new com.kodality.zmei.fhir.resource.terminology.ValueSet();
    termxWebUrl.ifPresent(url -> fhirValueSet.addExtension(toFhirWebSourceExtension(url, valueSet.getId())));
    fhirValueSet.setId(toFhirId(valueSet, version));
    fhirValueSet.setUrl(valueSet.getUri());
    fhirValueSet.setName(valueSet.getName());
    fhirValueSet.setTitle(toFhirName(valueSet.getTitle(), version.getPreferredLanguage()));
    fhirValueSet.setPrimitiveExtensions("title", toFhirTranslationExtension(valueSet.getTitle(), version.getPreferredLanguage()));
    fhirValueSet.setDescription(toFhirName(valueSet.getDescription(), version.getPreferredLanguage()));
    fhirValueSet.setPrimitiveExtensions("description", toFhirTranslationExtension(valueSet.getDescription(), version.getPreferredLanguage()));
    fhirValueSet.setPurpose(toFhirName(valueSet.getPurpose(), version.getPreferredLanguage()));
    fhirValueSet.setPrimitiveExtensions("purpose", toFhirTranslationExtension(valueSet.getPurpose(), version.getPreferredLanguage()));
    fhirValueSet.setContact(toFhirContacts(valueSet.getContacts()));
    fhirValueSet.setIdentifier(toFhirIdentifiers(valueSet.getIdentifiers()));
    fhirValueSet.setText(toFhirText(valueSet.getNarrative()));
    fhirValueSet.setPublisher(valueSet.getPublisher());
    fhirValueSet.setExperimental(valueSet.getExperimental() != null && valueSet.getExperimental());
    fhirValueSet.setLastReviewDate(toFhirDate(provenances, "reviewed"));
    fhirValueSet.setApprovalDate(toFhirDate(provenances, "approved"));
    if (valueSet.getCopyright() != null) {
      fhirValueSet.setCopyright(valueSet.getCopyright().getHolder());
      fhirValueSet.setCopyrightLabel(valueSet.getCopyright().getStatement());
      fhirValueSet.setJurisdiction(valueSet.getCopyright().getJurisdiction() != null ?
          List.of(new CodeableConcept().setText(valueSet.getCopyright().getJurisdiction())) : null);
    }
    if (valueSet.getPermissions() != null) {
      fhirValueSet.setAuthor(valueSet.getPermissions().getAdmin() != null ? toFhirContacts(valueSet.getPermissions().getAdmin()) : null);
      fhirValueSet.setEditor(valueSet.getPermissions().getEditor() != null ? toFhirContacts(valueSet.getPermissions().getEditor()) : null);
      fhirValueSet.setReviewer(valueSet.getPermissions().getViewer() != null ? toFhirContacts(valueSet.getPermissions().getViewer()) : null);
      fhirValueSet.setEndorser(valueSet.getPermissions().getEndorser() != null ? toFhirContacts(valueSet.getPermissions().getEndorser()) : null);
    }

    fhirValueSet.setVersion(version.getVersion());
    fhirValueSet.setLanguage(version.getPreferredLanguage());
    fhirValueSet.setVersionAlgorithmString(version.getAlgorithm());
    fhirValueSet.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirValueSet.setStatus(version.getStatus());
    fhirValueSet.setCompose(toFhirCompose(version.getRuleSet()));
    return fhirValueSet;
  }

  private static ValueSetCompose toFhirCompose(ValueSetVersionRuleSet ruleSet) {
    if (ruleSet == null) {
      return null;
    }
    ValueSetCompose compose = new ValueSetCompose();
    compose.setInactive(ruleSet.getInactive());
    if (ruleSet.getLockedDate() != null) {
      compose.setLockedDate(ruleSet.getLockedDate().toLocalDate());
    }
    compose.setProperty(ruleSet.getRules() == null ? List.of() :
        ruleSet.getRules().stream().flatMap(r -> Optional.ofNullable(r.getProperties()).orElse(List.of()).stream()).filter(StringUtils::isNotEmpty).toList());
    compose.setInclude(toFhirInclude(ruleSet.getRules(), ValueSetVersionRuleType.include));
    compose.setExclude(toFhirInclude(ruleSet.getRules(), ValueSetVersionRuleType.exclude));
    return compose;
  }

  private static List<ValueSetComposeInclude> toFhirInclude(List<ValueSetVersionRule> rules, String type) {
    if (CollectionUtils.isEmpty(rules)) {
      return null;
    }
    return rules.stream().filter(r -> r.getType().equals(type)).map(rule -> {
      ValueSetComposeInclude include = new ValueSetComposeInclude();
      include.setSystem(rule.getCodeSystemUri());
      include.setVersion(rule.getCodeSystemVersion() == null ? null : rule.getCodeSystemVersion().getVersion());
      include.setConcept(toFhirConcept(rule.getConcepts()));
      include.setFilter(toFhirFilter(rule.getFilters()));
      include.setValueSet(rule.getValueSetUri() == null ? null : List.of(rule.getValueSetUri()));
      return include;
    }).collect(toList());
  }

  private static List<ValueSetComposeIncludeConcept> toFhirConcept(List<ValueSetVersionConcept> concepts) {
    if (CollectionUtils.isEmpty(concepts)) {
      return null;
    }
    return concepts.stream().map(valueSetConcept -> {
      ValueSetComposeIncludeConcept concept = new ValueSetComposeIncludeConcept();
      concept.setCode(valueSetConcept.getConcept().getCode());
      concept.setDisplay(valueSetConcept.getDisplay() == null ? null : valueSetConcept.getDisplay().getName());
      concept.setDesignation(valueSetConcept.getAdditionalDesignations() == null ? new ArrayList<>() :
          valueSetConcept.getAdditionalDesignations().stream().map(d -> {
            ValueSetComposeIncludeConceptDesignation designation = new ValueSetComposeIncludeConceptDesignation();
            designation.setValue(d.getName());
            designation.setLanguage(d.getLanguage());
            return designation;
          }).collect(toList()));
      if (valueSetConcept.getOrderNumber() != null) {
        concept.setExtension(List.of(new Extension().setValueInteger(valueSetConcept.getOrderNumber()).setUrl(concept_order)));
      }
      return concept;
    }).collect(toList());
  }

  private static List<ValueSetComposeIncludeFilter> toFhirFilter(List<ValueSetRuleFilter> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return null;
    }
    return filters.stream().map(valueSetRuleFilter -> {
      ValueSetComposeIncludeFilter filter = new ValueSetComposeIncludeFilter();
      filter.setValue(JsonUtil.toJson(valueSetRuleFilter.getValue()));
      filter.setOp(valueSetRuleFilter.getOperator());
      filter.setProperty(valueSetRuleFilter.getProperty().getName());
      return filter;
    }).collect(toList());
  }

  public static com.kodality.zmei.fhir.resource.terminology.ValueSet toFhir(ValueSet valueSet, ValueSetVersion version, List<Provenance> provenances,
                                                                            List<ValueSetVersionConcept> concepts, Parameters param) {
    boolean active = Optional.ofNullable(param).map(p -> p.findParameter("activeOnly")
        .map(pp -> pp.getValueBoolean() != null ? pp.getValueBoolean() : "true".equals(pp.getValueString()))
        .orElse(false)).orElse(false);
    if (active) {
      concepts = concepts.stream().filter(ValueSetVersionConcept::isActive).toList();
    }

    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet = toFhir(valueSet, version, provenances);
    fhirValueSet.setExpansion(toFhirExpansion(concepts, fhirValueSet.getCompose().getProperty(), param));
    return fhirValueSet;
  }

  private static ValueSetExpansion toFhirExpansion(List<ValueSetVersionConcept> concepts, List<String> properties, Parameters param) {
    boolean flat = Optional.ofNullable(param).map(p -> p.findParameter("excludeNested")
        .map(pp -> pp.getValueBoolean() != null ? pp.getValueBoolean() : "true".equals(pp.getValueString())).orElse(false)).orElse(false);

    ValueSetExpansion expansion = new ValueSetExpansion();
    if (concepts == null) {
      return expansion;
    }
    expansion.setTotal(concepts.size());
    expansion.setParameter(toValueSetParameter(param));

    if (flat) {
      expansion.setContains(concepts.stream().map(c -> toFhirExpansionContains(c, properties, param)).collect(toList()));
    } else {
      expansion.setContains(getConceptsHierarchy(concepts, properties, param));
    }
    return expansion;
  }

  private static List<ValueSetExpansionParameter> toValueSetParameter(Parameters param) {
    if (param == null || param.getParameter() == null) {
      return null;
    }

    return param.getParameter().stream().map(p -> new ValueSetExpansionParameter().setName(p.getName())
        .setValueString(p.getValueString())
        .setValueBoolean(p.getValueBoolean())
        .setValueInteger(p.getValueInteger())
        .setValueDecimal(p.getValueDecimal())
        .setValueUri(p.getValueUri())
        .setValueCode(p.getValueCode())
        .setValueDateTime(p.getValueDateTime())
    ).toList();
  }

  private static ValueSetExpansionContains toFhirExpansionContains(ValueSetVersionConcept c, List<String> allProperties, Parameters param) {
    boolean includeDesignations = Optional.ofNullable(param).map(
        p -> p.findParameter("includeDesignations").map(pr -> pr.getValueBoolean() != null && pr.getValueBoolean() || "true".equals(pr.getValueString()))
            .orElse(false)).orElse(false);
    boolean includeDefinition = Optional.ofNullable(param).map(
        p -> p.findParameter("includeDefinition").map(pr -> pr.getValueBoolean() != null && pr.getValueBoolean() || "true".equals(pr.getValueString()))
            .orElse(false)).orElse(false);
    String lang = Optional.ofNullable(param).map(Resource::getLanguage).orElse(null);
    List<String> properties = ((param == null) || (param.getParameter() == null)) ? List.of() :
        param.getParameter().stream().filter(p -> "property".equals(p.getName())).map(ParametersParameter::getValueString).toList();

    ValueSetExpansionContains contains = new ValueSetExpansionContains();
    contains.setCode(c.getConcept().getCode());
    contains.setSystem(c.getConcept().getBaseCodeSystemUri() != null ? c.getConcept().getBaseCodeSystemUri() : c.getConcept().getCodeSystemUri());
    contains.setVersion(c.getConcept().getBaseCodeSystemUri() == null && c.getConcept().getCodeSystemVersions() != null ?
        c.getConcept().getCodeSystemVersions().stream().findFirst().orElse(null) : null);
    contains.setInactive(!c.isActive() ? true : null);
    contains.setDisplay(c.getDisplay() == null || (lang != null && !c.getDisplay().getLanguage().startsWith(lang)) ? null : c.getDisplay().getName());
    contains.setDesignation(CollectionUtils.isNotEmpty(c.getAdditionalDesignations()) && includeDesignations ? c.getAdditionalDesignations().stream()
        .filter(d -> !"definition".equals(d.getDesignationType()))
        .sorted(Comparator.comparing(d -> !d.isPreferred())).map(designation -> {
          ValueSetComposeIncludeConceptDesignation d = new ValueSetComposeIncludeConceptDesignation();
          d.setValue(designation.getName());
          d.setLanguage(designation.getLanguage());
          d.setUse(new Coding(designation.getDesignationType() == null ? "display" : designation.getDesignationType()));
          return d;
        }).collect(toList()) : new ArrayList<>());
    if (c.getDisplay() != null && (lang != null && !c.getDisplay().getLanguage().startsWith(lang))) {
      contains.getDesignation()
          .add(new ValueSetComposeIncludeConceptDesignation().setValue(c.getDisplay().getName()).setLanguage(c.getDisplay().getLanguage()));
    }
    contains.setExtension(CollectionUtils.isNotEmpty(c.getAdditionalDesignations()) && includeDefinition ? c.getAdditionalDesignations().stream()
        .filter(d -> "definition".equals(d.getDesignationType())).map(d -> {
          Extension extension = new Extension(concept_definition);
          extension.setValueString(d.getName());
          return extension;
        }).toList() : null);
    contains.setProperty(c.getPropertyValues() == null ? new ArrayList<>() :
        c.getPropertyValues().stream().filter(p -> CollectionUtils.isEmpty(properties) || properties.contains(p.getEntityProperty())).map(p -> {
          ValueSetExpansionContainsProperty property = new ValueSetExpansionContainsProperty();
          property.setCode(p.getEntityProperty());
          switch (p.getEntityPropertyType()) {
            case EntityPropertyType.code -> property.setValueCode((String) p.getValue());
            case EntityPropertyType.string -> property.setValueString((String) p.getValue());
            case EntityPropertyType.bool -> property.setValueBoolean((Boolean) p.getValue());
            case EntityPropertyType.decimal -> property.setValueDecimal(new BigDecimal(String.valueOf(p.getValue())));
            case EntityPropertyType.integer -> property.setValueInteger(Integer.valueOf(String.valueOf(p.getValue())));
            case EntityPropertyType.coding -> {
              Concept concept = JsonUtil.getObjectMapper().convertValue(p.getValue(), Concept.class);
              property.setValueCoding(new Coding(concept.getCodeSystem(), concept.getCode()));
            }
            case EntityPropertyType.dateTime -> {
              if (p.getValue() instanceof OffsetDateTime) {
                property.setValueDateTime((OffsetDateTime) p.getValue());
              } else {
                property.setValueDateTime(DateUtil.parseOffsetDateTime((String) p.getValue()));
              }
            }
          }
          return property;
        }).collect(toList()));
    if (allProperties.contains("status") && (CollectionUtils.isEmpty(properties) || properties.contains("status"))) {
      contains.getProperty().add(new ValueSetExpansionContainsProperty().setCode("status").setValueCode(PublicationStatus.getStatus(c.getStatus())));
    }
    if (allProperties.contains("parent") && (CollectionUtils.isEmpty(properties) || properties.contains("parent")) && c.getAssociations() != null) {
      c.getAssociations().forEach(a -> contains.getProperty().add(new ValueSetExpansionContainsProperty().setCode("parent").setValueCode(a.getTargetCode())));
    }
    return contains;
  }

  private static List<ValueSetExpansionContains> getConceptsHierarchy(List<ValueSetVersionConcept> concepts, List<String> properties, Parameters param) {
    Map<String, List<ValueSetVersionConcept>> targetCodeConcepts = concepts.stream()
        .filter(c -> !c.isEnumerated() && CollectionUtils.isNotEmpty(c.getAssociations()))
        .flatMap(c -> c.getAssociations().stream().filter(a -> a.getTargetCode() != null).map(a -> Pair.of(c, a)))
        .collect(Collectors.groupingBy(c -> c.getRight().getTargetCode(), Collectors.mapping(Pair::getLeft, toList())));
    List<String> codes = concepts.stream().map(c -> c.getConcept().getCode()).distinct().toList();
    List<ValueSetVersionConcept> rootConcepts = concepts.stream().filter(c -> c.isEnumerated() ||
        CollectionUtils.isEmpty(c.getAssociations()) ||
        c.getAssociations().stream().noneMatch(a -> codes.contains(a.getTargetCode()))).toList();
    return rootConcepts.stream()
        .map(c -> {
          ValueSetExpansionContains contains = toFhirExpansionContains(c, properties, param);
          contains.setContains(getChildConcepts(targetCodeConcepts, c.getConcept().getCode(), properties, param));
          return contains;
        }).toList();
  }

  private static List<ValueSetExpansionContains> getChildConcepts(Map<String, List<ValueSetVersionConcept>> targetCodeConcepts, String targetCode,
                                                                  List<String> properties, Parameters param) {
    return targetCodeConcepts.getOrDefault(targetCode, List.of()).stream()
        .map(c -> {
          ValueSetExpansionContains contains = toFhirExpansionContains(c, properties, param);
          contains.setContains(getChildConcepts(targetCodeConcepts, c.getConcept().getCode(), properties, param));
          return contains;
        }).toList();
  }

  public static ValueSetQueryParams fromFhir(SearchCriterion fhir) {
    ValueSetQueryParams params = new ValueSetQueryParams();
    getSimpleParams(fhir).forEach((k, v) -> {
      switch (k) {
        case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
        case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
        case "_id" -> params.setIds(v);
        case "version" -> params.setVersionVersion(v);
        case "url" -> params.setUri(v);
        case "name" -> params.setName(v);
        case "name:contains" -> params.setNameContains(v);
        case "title" -> params.setTitle(v);
        case "title:contains" -> params.setTitleContains(v);
        case "status" -> params.setVersionStatus(v);
        case "reference" -> params.setCodeSystemUri(v);
        case "publisher" -> params.setVersionSource(v);
        case "description" -> params.setDescriptionContains(v);
        case "code" -> params.setConceptCode(v);
        default -> throw new ApiClientException("Search by '" + k + "' not supported");
      }
    });
    params.setDecorated(true);
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.VS_VIEW));
    return params;
  }

  public static ValueSetVersionQueryParams fromFhirVSVersionParams(SearchCriterion fhir) {
    ValueSetVersionQueryParams params = new ValueSetVersionQueryParams();
    getSimpleParams(fhir).forEach((k, v) -> {
      switch (k) {
        case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
        case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
        case "_id" -> params.setValueSet(v);
        case "version" -> params.setVersion(v);
        case "url" -> params.setValueSetUri(v);
        case "name" -> params.setValueSetName(v);
        case "name:contains" -> params.setValueSetNameContains(v);
        case "title" -> params.setValueSetTitle(v);
        case "status" -> params.setStatus(v);
        case "reference" -> params.setCodeSystemUri(v);
        case "publisher" -> params.setValueSetPublisher(v);
        case "description" -> params.setValueSetDescriptionContains(v);
        case "code" -> params.setConceptCode(v);
        default -> throw new ApiClientException("Search by '" + k + "' not supported");
      }
    });
    params.setPermittedValueSets(SessionStore.require().getPermittedResourceIds(Privilege.VS_VIEW));
    return params;
  }

  // -------------- FROM FHIR --------------

  public static ValueSet fromFhirValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSet vs = new ValueSet();
    vs.setId(CodeSystemFhirMapper.parseCompositeId(valueSet.getId())[0]);
    vs.setUri(valueSet.getUrl());
    vs.setPublisher(valueSet.getPublisher());
    vs.setName(valueSet.getName());
    vs.setTitle(fromFhirName(valueSet.getTitle(), valueSet.getLanguage()));
    vs.setDescription(fromFhirName(valueSet.getDescription(), valueSet.getLanguage()));
    vs.setPurpose(fromFhirName(valueSet.getPurpose(), valueSet.getLanguage()));
    vs.setNarrative(valueSet.getText() == null ? null : valueSet.getText().getDiv());
    vs.setIdentifiers(fromFhirIdentifiers(valueSet.getIdentifier()));
    vs.setContacts(fromFhirContacts(valueSet.getContact()));
    vs.setVersions(List.of(fromFhirVersion(valueSet)));
    vs.setExperimental(valueSet.getExperimental());
    vs.setCopyright(new Copyright().setHolder(valueSet.getCopyright()).setStatement(valueSet.getCopyrightLabel()));
    vs.setPermissions(new Permissions().setAdmin(fromFhirContactsName(valueSet.getAuthor()))
        .setEditor(fromFhirContactsName(valueSet.getEditor()))
        .setViewer(fromFhirContactsName(valueSet.getReviewer()))
        .setEndorser(fromFhirContactsName(valueSet.getEndorser())));
    return vs;
  }

  private static ValueSetSnapshot fromFhirExpansion(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSetExpansion expansion = valueSet.getExpansion();
    if (expansion == null) {
      return null;
    }
    return new ValueSetSnapshot().setExpansion(expansion.getContains().stream().map(c -> new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConceptValue().setCode(c.getCode()).setCodeSystemUri(c.getSystem()))
        .setDisplay(new Designation().setName(c.getDisplay()).setLanguage(Optional.ofNullable(valueSet.getLanguage()).orElse(Language.en)))
        .setAdditionalDesignations(Optional.ofNullable(c.getDesignation()).orElse(List.of()).stream().map(d -> new Designation().setName(d.getValue()).setLanguage(d.getLanguage())).toList())
        .setActive(c.getInactive() == null || !c.getInactive())
    ).toList());
  }

  private static ValueSetVersion fromFhirVersion(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSetVersion version = new ValueSetVersion();
    version.setValueSet(valueSet.getId());
    version.setVersion(valueSet.getVersion() == null ? "1.0.0" : valueSet.getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setPreferredLanguage(valueSet.getLanguage());
    version.setReleaseDate(valueSet.getDate() == null ? LocalDate.now() : LocalDate.from(valueSet.getDate()));
    version.setRuleSet(fromFhirCompose(valueSet));
    version.setSnapshot(fromFhirExpansion(valueSet));
    return version;
  }

  private static ValueSetVersionRuleSet fromFhirCompose(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    if (valueSet.getCompose() == null) {
      return null;
    }
    ValueSetVersionRuleSet ruleSet = new ValueSetVersionRuleSet();
    ruleSet.setInactive(valueSet.getCompose().getInactive());
    if (valueSet.getCompose().getLockedDate() != null) {
      ruleSet.setLockedDate(valueSet.getCompose().getLockedDate().atStartOfDay().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }
    ruleSet.setRules(fromFhirRules(valueSet.getCompose().getInclude(), valueSet.getCompose().getExclude()));
    return ruleSet;
  }

  private static List<ValueSetVersionRule> fromFhirRules(List<ValueSetComposeInclude> include, List<ValueSetComposeInclude> exclude) {
    List<ValueSetVersionRule> rules = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(include)) {
      rules.addAll(include.stream().map(inc -> fromFhirInclude(inc, ValueSetVersionRuleType.include)).toList());
    }
    if (CollectionUtils.isNotEmpty(exclude)) {
      rules.addAll(exclude.stream().map(exc -> fromFhirInclude(exc, ValueSetVersionRuleType.exclude)).toList());
    }
    return rules;
  }

  private static ValueSetVersionRule fromFhirInclude(ValueSetComposeInclude r, String type) {
    ValueSetVersionRule rule = new ValueSetVersionRule();
    rule.setType(type);
    rule.setCodeSystemUri(r.getSystem());
    rule.setConcepts(fromFhirConcepts(r.getConcept()));
    rule.setFilters(fromFhirFilters(r.getFilter()));
    rule.setValueSetUri(CollectionUtils.isEmpty(r.getValueSet()) ? null : r.getValueSet().get(0));
    return rule;
  }

  private static List<ValueSetVersionConcept> fromFhirConcepts(List<ValueSetComposeIncludeConcept> concepts) {
    if (CollectionUtils.isEmpty(concepts)) {
      return null;
    }
    return concepts.stream().map(c -> {
      ValueSetVersionConcept concept = new ValueSetVersionConcept();
      concept.setConcept(new ValueSetVersionConceptValue().setCode(c.getCode()));
      concept.setDisplay(c.getDisplay() != null ? new Designation().setName(c.getDisplay()) : null);
      concept.setAdditionalDesignations(fromFhirDesignations(c.getDesignation()));
      return concept;
    }).collect(Collectors.toList());
  }

  private static List<Designation> fromFhirDesignations(List<ValueSetComposeIncludeConceptDesignation> designation) {
    if (CollectionUtils.isEmpty(designation)) {
      return null;
    }
    return designation.stream().map(d -> new Designation()
        .setLanguage(d.getLanguage() == null ? Language.en : d.getLanguage())
        .setName(d.getValue())
        .setDesignationKind("text")
        .setCaseSignificance(CaseSignificance.entire_term_case_insensitive)
        .setStatus(PublicationStatus.active)).collect(Collectors.toList());
  }

  private static List<ValueSetRuleFilter> fromFhirFilters(List<ValueSetComposeIncludeFilter> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return null;
    }
    return filters.stream().map(f -> {
      EntityProperty ep = new EntityProperty();
      ep.setName(f.getProperty());

      ValueSetRuleFilter filter = new ValueSetRuleFilter();
      filter.setProperty(ep);
      filter.setOperator(f.getOp());
      filter.setValue(f.getValue());
      return filter;
    }).collect(Collectors.toList());
  }
}
