package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.Language;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.termserver.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
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
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class ValueSetFhirMapper {

  public com.kodality.zmei.fhir.resource.terminology.ValueSet toFhir(ValueSet valueSet, ValueSetVersion version) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet = new com.kodality.zmei.fhir.resource.terminology.ValueSet();
    fhirValueSet.setId(valueSet.getId());
    fhirValueSet.setUrl(valueSet.getUri());
    //TODO identifiers from naming-system
    fhirValueSet.setName(valueSet.getNames().getOrDefault(Language.en, valueSet.getNames().values().stream().findFirst().orElse(null)));
    fhirValueSet.setContact(valueSet.getContacts() == null ? null : valueSet.getContacts().stream()
        .map(c -> new ContactDetail().setName(c.getName()).setTelecom(c.getTelecoms() == null ? null : c.getTelecoms().stream().map(t ->
            new ContactPoint().setSystem(t.getSystem()).setValue(t.getValue()).setUse(t.getUse())).collect(Collectors.toList())))
        .collect(Collectors.toList()));
    fhirValueSet.setText(valueSet.getNarrative() == null ? null : new Narrative().setDiv(valueSet.getNarrative()));
    fhirValueSet.setDescription(valueSet.getDescription());
    fhirValueSet.setVersion(version.getVersion());
    fhirValueSet.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirValueSet.setStatus(version.getStatus());
    fhirValueSet.setPublisher(version.getSource());
    fhirValueSet.setCompose(toFhirCompose(version.getRuleSet()));

    return fhirValueSet;
  }

  private ValueSetCompose toFhirCompose(ValueSetVersionRuleSet ruleSet) {
    ValueSetCompose compose = new ValueSetCompose();
    compose.setInactive(ruleSet.getInactive());
    compose.setLockedDate(ruleSet.getLockedDate());
    compose.setInclude(toFhirInclude(ruleSet.getRules(), ValueSetVersionRuleType.include));
    compose.setExclude(toFhirInclude(ruleSet.getRules(), ValueSetVersionRuleType.exclude));
    return compose;
  }

  private List<ValueSetComposeInclude> toFhirInclude(List<ValueSetVersionRule> rules, String type) {
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

  private List<ValueSetComposeIncludeConcept> toFhirConcept(List<ValueSetVersionConcept> concepts) {
    if (CollectionUtils.isEmpty(concepts)) {
      return null;
    }
    return concepts.stream().map(valueSetConcept -> {
      ValueSetComposeIncludeConcept concept = new ValueSetComposeIncludeConcept();
      concept.setCode(valueSetConcept.getConcept().getCode());
      concept.setDisplay(valueSetConcept.getDisplay().getName());
      concept.setDesignation(valueSetConcept.getAdditionalDesignations().stream().map(d -> {
        ValueSetComposeIncludeConceptDesignation designation = new ValueSetComposeIncludeConceptDesignation();
        designation.setValue(d.getName());
        designation.setLanguage(d.getLanguage());
        return designation;
      }).collect(Collectors.toList()));
      return concept;
    }).collect(Collectors.toList());
  }

  private List<ValueSetComposeIncludeFilter> toFhirFilter(List<ValueSetRuleFilter> filters) {
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

  public com.kodality.zmei.fhir.resource.terminology.ValueSet toFhir(ValueSet valueSet, ValueSetVersion version, List<ValueSetVersionConcept> concepts) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet = toFhir(valueSet, version);
    fhirValueSet.setExpansion(toFhirExpansion(concepts));
    return fhirValueSet;
  }

  private ValueSetExpansion toFhirExpansion(List<ValueSetVersionConcept> concepts) {
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
      contains.setDesignation(valueSetConcept.getAdditionalDesignations() == null ? null : valueSetConcept.getAdditionalDesignations().stream().map(designation -> {
        ValueSetComposeIncludeConceptDesignation d = new ValueSetComposeIncludeConceptDesignation();
        d.setValue(designation.getName());
        d.setLanguage(designation.getLanguage());
        return d;
      }).collect(Collectors.toList()));
      return contains;
    }).collect(Collectors.toList()));
    return expansion;
  }

}
