package com.kodality.termx.fhir.valueset;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.fhir.BaseFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Narrative;
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
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Context
public class ValueSetFhirMapper extends BaseFhirMapper {
  private static Optional<String> termxWebUrl;
  private static final String concept_definition = "http://hl7.org/fhir/StructureDefinition/valueset-concept-definition";
  private static final String concept_order = "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder";

  public ValueSetFhirMapper(@Value("${termx.web-url}") Optional<String> termxWebUrl) {
    ValueSetFhirMapper.termxWebUrl = termxWebUrl;
  }

  public static String toFhirId(ValueSet vs, ValueSetVersion vsv) {
    return vs.getId() + "@" + vsv.getVersion();
  }

  public static String toFhirJson(ValueSet vs, ValueSetVersion vsv, List<Provenance> provenances) {
    return FhirMapper.toJson(toFhir(vs, vsv, provenances));
  }

  public static com.kodality.zmei.fhir.resource.terminology.ValueSet toFhir(ValueSet valueSet, ValueSetVersion version, List<Provenance> provenances) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet = new com.kodality.zmei.fhir.resource.terminology.ValueSet();
    termxWebUrl.ifPresent(url -> fhirValueSet.addExtension(new Extension("http://hl7.org/fhir/tools/StructureDefinition/web-source")
        .setValueUrl(url + "/fhir/ValueSet/" + valueSet.getId())));
    fhirValueSet.setId(toFhirId(valueSet, version));
    fhirValueSet.setUrl(valueSet.getUri());
    fhirValueSet.setName(valueSet.getName());
    fhirValueSet.setTitle(toFhirName(valueSet.getTitle()));
    fhirValueSet.setDescription(toFhirName(valueSet.getDescription()));
    fhirValueSet.setPurpose(toFhirName(valueSet.getPurpose()));
    fhirValueSet.setContact(toFhirContacts(valueSet.getContacts()));
    fhirValueSet.setIdentifier(toFhirIdentifiers(valueSet.getIdentifiers()));
    fhirValueSet.setText(valueSet.getNarrative() == null ? null : new Narrative().setDiv(valueSet.getNarrative()));
    fhirValueSet.setPublisher(valueSet.getPublisher());
    fhirValueSet.setExperimental(valueSet.getExperimental() != null && valueSet.getExperimental());
    fhirValueSet.setLastReviewDate(Optional.ofNullable(provenances).flatMap(list -> list.stream().filter(p -> "reviewed".equals(p.getActivity()))
        .max(Comparator.comparing(Provenance::getDate)).map(p -> p.getDate().toLocalDate())).orElse(null));
    fhirValueSet.setApprovalDate(Optional.ofNullable(provenances).flatMap(list -> list.stream().filter(p -> "approved".equals(p.getActivity()))
        .max(Comparator.comparing(Provenance::getDate)).map(p -> p.getDate().toLocalDate())).orElse(null));
    fhirValueSet.setCopyright(valueSet.getCopyright() != null ? valueSet.getCopyright().getHolder() : null);
    fhirValueSet.setCopyrightLabel(valueSet.getCopyright() != null ? valueSet.getCopyright().getStatement() : null);
    fhirValueSet.setJurisdiction(valueSet.getCopyright() != null && valueSet.getCopyright().getJurisdiction() != null ?
        List.of(new CodeableConcept().setText(valueSet.getCopyright().getJurisdiction())) : null);

    fhirValueSet.setVersion(version.getVersion());
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
      include.setConcept(toFhirConcept(rule.getConcepts()));
      include.setFilter(toFhirFilter(rule.getFilters()));
      include.setValueSet(rule.getValueSetUri());
      return include;
    }).collect(Collectors.toList());
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
          }).collect(Collectors.toList()));
      if (valueSetConcept.getOrderNumber() != null) {
        concept.setExtension(List.of(new Extension().setValueInteger(valueSetConcept.getOrderNumber()).setUrl(concept_order)));
      }
      return concept;
    }).collect(Collectors.toList());
  }

  private static List<ValueSetComposeIncludeFilter> toFhirFilter(List<ValueSetRuleFilter> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return null;
    }
    return filters.stream().map(valueSetRuleFilter -> {
      ValueSetComposeIncludeFilter filter = new ValueSetComposeIncludeFilter();
      filter.setValue(valueSetRuleFilter.getValue());
      filter.setOp(valueSetRuleFilter.getOperator());
      filter.setProperty(valueSetRuleFilter.getProperty().getName());
      return filter;
    }).collect(Collectors.toList());
  }

  public static com.kodality.zmei.fhir.resource.terminology.ValueSet toFhir(ValueSet valueSet, ValueSetVersion version, List<Provenance> provenances,
                                                                            List<ValueSetVersionConcept> concepts, Parameters param) {
    boolean flat = Optional.ofNullable(param).map(p -> p.findParameter("excludeNested").map(ParametersParameter::getValueBoolean).orElse(false)).orElse(false);
    boolean active = Optional.ofNullable(param).map(p -> p.findParameter("activeOnly").map(ParametersParameter::getValueBoolean).orElse(false)).orElse(false);
    boolean includeDesignations = Optional.ofNullable(param).map(p -> p.findParameter("includeDesignations").map(pr -> pr.getValueBoolean() != null && pr.getValueBoolean() || "true".equals(pr.getValueString())).orElse(false)).orElse(false);
    boolean includeDefinition = Optional.ofNullable(param).map(p -> p.findParameter("includeDefinition").map(pr -> pr.getValueBoolean()  != null && pr.getValueBoolean() || "true".equals(pr.getValueString())).orElse(false)).orElse(false);
    String language = Optional.ofNullable(param).map(Resource::getLanguage).orElse(null);

    if (active) {
      concepts = concepts.stream().filter(ValueSetVersionConcept::isActive).toList();
    }

    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet = toFhir(valueSet, version, provenances);
    fhirValueSet.setExpansion(toFhirExpansion(concepts, flat, includeDesignations, includeDefinition, language));
    return fhirValueSet;
  }

  private static ValueSetExpansion toFhirExpansion(List<ValueSetVersionConcept> concepts, boolean flat,
                                                   boolean includeDesignations, boolean includeDefinition,
                                                   String lang) {
    ValueSetExpansion expansion = new ValueSetExpansion();
    if (concepts == null) {
      return expansion;
    }
    expansion.setTotal(concepts.size());

    if (flat) {
      expansion.setContains(concepts.stream().map(c -> toFhirExpansionContains(c, lang, includeDesignations, includeDefinition)).collect(Collectors.toList()));
    } else {
      expansion.setContains(getChildConcepts(concepts, null, lang, includeDesignations, includeDefinition));
    }
    return expansion;
  }

  private static ValueSetExpansionContains toFhirExpansionContains(ValueSetVersionConcept c, String lang, boolean includeDesignations,
                                                                   boolean includeDefinition) {
    ValueSetExpansionContains contains = new ValueSetExpansionContains();
    contains.setCode(c.getConcept().getCode());
    contains.setSystem(c.getConcept().getCodeSystemUri());
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
        }).collect(Collectors.toList()) : new ArrayList<>());
    if (c.getDisplay() != null && (lang != null && !c.getDisplay().getLanguage().startsWith(lang))) {
      contains.getDesignation().add(new ValueSetComposeIncludeConceptDesignation().setValue(c.getDisplay().getName()).setLanguage(c.getDisplay().getLanguage()));
    }
    contains.setExtension(CollectionUtils.isNotEmpty(c.getAdditionalDesignations()) && includeDefinition ? c.getAdditionalDesignations().stream()
        .filter(d -> "definition".equals(d.getDesignationType())).map(d -> {
          Extension extension = new Extension(concept_definition);
          extension.setValueString(d.getName());
          return extension;
        }).toList() : null);
    //TODO properties and associations
    return contains;
  }

  private static List<ValueSetExpansionContains> getChildConcepts(List<ValueSetVersionConcept> concepts, String targetCode, String lang,
                                                                  boolean includeDesignations, boolean includeDefinition) {
    if (targetCode == null) {
      return concepts.stream()
          .filter(c -> c.isEnumerated() || CollectionUtils.isEmpty(c.getAssociations()) || c.getAssociations().stream()
              .noneMatch(a -> concepts.stream().map(concept -> concept.getConcept().getCode()).anyMatch(code -> code.equals(a.getTargetCode()))))
          .map(c -> {
            ValueSetExpansionContains contains = toFhirExpansionContains(c, lang, includeDesignations, includeDefinition);
            contains.setContains(getChildConcepts(concepts, c.getConcept().getCode(), lang, includeDesignations, includeDefinition));
            return contains;
          }).toList();
    }

    return concepts.stream()
        .filter(c -> !c.isEnumerated())
        .filter(c -> CollectionUtils.isNotEmpty(c.getAssociations()))
        .filter(c -> c.getAssociations().stream().anyMatch(a -> targetCode.equals(a.getTargetCode())))
        .map(c -> {
          ValueSetExpansionContains contains = toFhirExpansionContains(c, lang, includeDesignations, includeDefinition);
          contains.setContains(getChildConcepts(concepts, c.getConcept().getCode(), lang, includeDesignations, includeDefinition));
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
        case "name", "title" -> params.setNameContains(v);
        case "name:contains" -> params.setNameContains(v);
        case "status" -> params.setVersionStatus(v);
        case "reference" -> params.setCodeSystemUri(v);
        case "publisher" -> params.setVersionSource(v);
        case "description" -> params.setDescriptionContains(v);
        case "code" -> params.setConceptCode(v);
        default -> throw new ApiClientException("Search by '" + k + "' not supported");
      }
    });
    params.setDecorated(true);
    return params;
  }

}
