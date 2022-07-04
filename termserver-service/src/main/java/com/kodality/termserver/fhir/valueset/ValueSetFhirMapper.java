package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.Language;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetConcept;
import com.kodality.termserver.valueset.ValueSetRuleSet;
import com.kodality.termserver.valueset.ValueSetRuleSet.ValueSetRule;
import com.kodality.termserver.valueset.ValueSetRuleSet.ValueSetRule.ValueSetRuleFilter;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Compose;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Concept;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Designation;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Filter;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Include;
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

  private Compose toFhirCompose(ValueSetRuleSet ruleSet) {
    Compose compose = new Compose();
    compose.setInactive(ruleSet.getInactive());
    compose.setLockedDate(ruleSet.getLockedDate());
    compose.setInclude(toFhirInclude(ruleSet.getIncludeRules()));
    compose.setExclude(toFhirInclude(ruleSet.getExcludeRules()));
    return compose;
  }

  private List<Include> toFhirInclude(List<ValueSetRule> rules) {
    if (CollectionUtils.isEmpty(rules)) {
      return null;
    }
    return rules.stream().map(rule -> {
      Include include = new Include();
      include.setSystem(rule.getCodeSystem());
      include.setVersion(rule.getCodeSystemVersion());
      include.setConcept(toFhirConcept(rule.getConcepts()));
      include.setFilter(toFhirFilter(rule.getFilters()));
      include.setValueSet(rule.getValueSet());
      return include;
    }).collect(Collectors.toList());
  }

  private List<Concept> toFhirConcept(List<ValueSetConcept> concepts) {
    if (CollectionUtils.isEmpty(concepts)) {
      return null;
    }
    return concepts.stream().map(valueSetConcept -> {
      Concept concept = new Concept();
      concept.setCode(valueSetConcept.getConcept().getCode());
      concept.setDisplay(valueSetConcept.getDisplay().getName());
      concept.setDesignation(valueSetConcept.getAdditionalDesignations().stream().map(d -> {
        Designation designation = new Designation();
        designation.setValue(d.getName());
        designation.setLanguage(d.getLanguage());
        return designation;
      }).collect(Collectors.toList()));
      return concept;
    }).collect(Collectors.toList());
  }

  private List<Filter> toFhirFilter(List<ValueSetRuleFilter> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return null;
    }
    return filters.stream().map(valueSetRuleFilter -> {
      Filter filter = new Filter();
      filter.setValue(valueSetRuleFilter.getValue());
      filter.setOp(valueSetRuleFilter.getOperator());
      filter.setProperty(valueSetRuleFilter.getProperty().getName());
      return filter;
    }).collect(Collectors.toList());
  }

}
