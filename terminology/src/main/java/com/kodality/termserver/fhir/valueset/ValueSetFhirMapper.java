package com.kodality.termserver.fhir.valueset;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termserver.fhir.BaseFhirMapper;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetCompose;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConcept;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeFilter;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains;
import io.micronaut.core.util.CollectionUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ValueSetFhirMapper extends BaseFhirMapper {
  private static final String concept_order = "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder";

  public static String toFhirId(ValueSet vs, ValueSetVersion vsv) {
    return vs.getId() + "|" + vsv.getVersion();
  }

  public static String toFhirJson(ValueSet vs, ValueSetVersion vsv) {
    return FhirMapper.toJson(toFhir(vs, vsv));
  }

  public static com.kodality.zmei.fhir.resource.terminology.ValueSet toFhir(ValueSet valueSet, ValueSetVersion version) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet = new com.kodality.zmei.fhir.resource.terminology.ValueSet();
    fhirValueSet.setId(toFhirId(valueSet, version));
    fhirValueSet.setUrl(valueSet.getUri());
    //TODO identifiers from naming-system
    fhirValueSet.setName(valueSet.getNames().getOrDefault(Language.en, valueSet.getNames().values().stream().findFirst().orElse(null)));
    fhirValueSet.setContact(toFhir(valueSet.getContacts()));
    fhirValueSet.setText(valueSet.getNarrative() == null ? null : new Narrative().setDiv(valueSet.getNarrative()));
    fhirValueSet.setDescription(valueSet.getDescription());

    fhirValueSet.setVersion(version.getVersion());
    fhirValueSet.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirValueSet.setStatus(version.getStatus());
    fhirValueSet.setPublisher(version.getSource());
    fhirValueSet.setCompose(toFhirCompose(version.getRuleSet()));

    return fhirValueSet;
  }

  private static ValueSetCompose toFhirCompose(ValueSetVersionRuleSet ruleSet) {
    if (ruleSet == null) {
      return null;
    }
    ValueSetCompose compose = new ValueSetCompose();
    compose.setInactive(ruleSet.getInactive());
    compose.setLockedDate(ruleSet.getLockedDate() == null ? null : ruleSet.getLockedDate().toLocalDate());
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
      include.setSystem(rule.getCodeSystem());
      include.setConcept(toFhirConcept(rule.getConcepts()));
      include.setFilter(toFhirFilter(rule.getFilters()));
      include.setValueSet(rule.getValueSet());
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

  public static com.kodality.zmei.fhir.resource.terminology.ValueSet toFhir(ValueSet valueSet, ValueSetVersion version, List<ValueSetVersionConcept> concepts) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet = toFhir(valueSet, version);
    fhirValueSet.setExpansion(toFhirExpansion(concepts));
    return fhirValueSet;
  }

  private static ValueSetExpansion toFhirExpansion(List<ValueSetVersionConcept> concepts) {
    ValueSetExpansion expansion = new ValueSetExpansion();
    if (concepts == null) {
      return expansion;
    }
    expansion.setTotal(concepts.size());
    expansion.setContains(concepts.stream().map(valueSetConcept -> {
      ValueSetExpansionContains contains = new ValueSetExpansionContains();
      contains.setCode(valueSetConcept.getConcept().getCode());
      contains.setSystem(valueSetConcept.getConcept().getCodeSystem());
      contains.setDisplay(valueSetConcept.getDisplay() == null ? null : valueSetConcept.getDisplay().getName());
      contains.setDesignation(
          valueSetConcept.getAdditionalDesignations() == null ? new ArrayList<>() : valueSetConcept.getAdditionalDesignations().stream()
              .sorted(Comparator.comparing(d -> !d.isPreferred()))
              .map(designation -> {
                ValueSetComposeIncludeConceptDesignation d = new ValueSetComposeIncludeConceptDesignation();
                d.setValue(designation.getName());
                d.setLanguage(designation.getLanguage());
                d.setUse(new Coding(designation.getDesignationType() == null ? "display" : designation.getDesignationType()));
                return d;
              }).collect(Collectors.toList()));
      contains.getDesignation().addAll(
          valueSetConcept.getConcept().getVersions() == null ? new ArrayList<>() :
              valueSetConcept.getConcept().getVersions().stream().filter(v -> v.getPropertyValues() != null)
                  .flatMap(v ->
                      v.getPropertyValues().stream().map(pv -> {
                        ValueSetComposeIncludeConceptDesignation d = new ValueSetComposeIncludeConceptDesignation();
                        d.setValue(JsonUtil.toJson(pv.getValue()));
                        d.setUse(new Coding(pv.getEntityProperty()));
                        return d;
                      })).toList()
      );
      contains.getDesignation().addAll(
          valueSetConcept.getConcept().getVersions() == null ? new ArrayList<>() :
              valueSetConcept.getConcept().getVersions().stream().filter(v -> v.getAssociations() != null)
                  .flatMap(v ->
                      v.getAssociations().stream().map(a -> {
                        ValueSetComposeIncludeConceptDesignation d = new ValueSetComposeIncludeConceptDesignation();
                        d.setValue(a.getTargetCode());
                        d.setUse(new Coding(a.getAssociationType()));
                        return d;
                      })).toList()
      );
      return contains;
    }).collect(Collectors.toList()));
    return expansion;
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
