package com.kodality.termserver.fhir.valueset;


import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.ContactDetail;
import com.kodality.termserver.ContactDetail.Telecom;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Concept;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Filter;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Include;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ValueSetFhirImportMapper {

  public static ValueSet mapValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSet vs = new ValueSet();
    vs.setId(valueSet.getId());
    vs.setUri(valueSet.getUrl());
    vs.setNames(new LocalizedName(Map.of(Language.en, valueSet.getName())));
    vs.setContacts(valueSet.getContact() == null ? null :
        valueSet.getContact().stream().map(ValueSetFhirImportMapper::mapContact).collect(Collectors.toList()));
    vs.setNarrative(valueSet.getText() == null ? null : valueSet.getText().getDiv());
    vs.setDescription(valueSet.getDescription());
    vs.setVersions(List.of(mapVersion(valueSet)));
    return vs;
  }

  private static ContactDetail mapContact(com.kodality.zmei.fhir.datatypes.ContactDetail c) {
    return new ContactDetail()
        .setName(c.getName())
        .setTelecoms(c.getTelecom() == null ? null : c.getTelecom().stream().map(t ->
            new Telecom().setSystem(t.getSystem()).setUse(t.getUse()).setValue(t.getValue())
        ).collect(Collectors.toList()));
  }

  private static ValueSetVersion mapVersion(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSetVersion version = new ValueSetVersion();
    version.setValueSet(valueSet.getId());
    version.setVersion(valueSet.getVersion());
    version.setSource(valueSet.getPublisher());
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(LocalDate.from(valueSet.getDate()));
    version.setRuleSet(mapRuleSet(valueSet));
    version.setConcepts(mapConcepts(valueSet));
    return version;
  }

  private static ValueSetVersionRuleSet mapRuleSet(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    if (valueSet.getCompose() == null) {
      return null;
    }
    ValueSetVersionRuleSet ruleSet = new ValueSetVersionRuleSet();
    ruleSet.setInactive(valueSet.getCompose().getInactive());
    ruleSet.setLockedDate(valueSet.getCompose().getLockedDate());
    ruleSet.setRules(mapRules(valueSet.getCompose().getInclude(), valueSet.getCompose().getExclude()));
    return ruleSet;
  }

  private static List<ValueSetVersionRule> mapRules(List<Include> include, List<Include> exclude) {
    List<ValueSetVersionRule> rules = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(include)) {
      rules.addAll(include.stream().map(inc -> mapRule(inc, ValueSetVersionRuleType.include)).toList());
    }
    if (CollectionUtils.isNotEmpty(exclude)) {
      rules.addAll(exclude.stream().map(exc -> mapRule(exc, ValueSetVersionRuleType.exclude)).toList());
    }
    return rules;
  }

  private static ValueSetVersionRule mapRule(Include r, String type) {
    ValueSetVersionRule rule = new ValueSetVersionRule();
    rule.setType(type);
    rule.setCodeSystem(r.getSystem());
    rule.setConcepts(mapRuleConcepts(r.getConcept()));
    rule.setFilters(mapRuleFilters(r.getFilter()));
    rule.setValueSet(r.getValueSet());
    return rule;
  }

  private static List<ValueSetVersionConcept> mapRuleConcepts(List<Concept> concepts) {
    if (CollectionUtils.isEmpty(concepts)) {
      return null;
    }
    return concepts.stream().map(c -> {
      ValueSetVersionConcept concept = new ValueSetVersionConcept();
      concept.setConcept(new com.kodality.termserver.codesystem.Concept().setCode(c.getCode()));
      concept.setAdditionalDesignations(mapDesignations(c.getDesignation()));
      concept.setDisplay(new Designation()
          .setName(c.getDisplay())
          .setDesignationKind("text")
          .setCaseSignificance(CaseSignificance.entire_term_case_insensitive)
          .setStatus(PublicationStatus.active));
      return concept;
    }).collect(Collectors.toList());
  }

  private static List<Designation> mapDesignations(List<com.kodality.zmei.fhir.resource.terminology.ValueSet.Designation> designation) {
    if (CollectionUtils.isEmpty(designation)) {
      return null;
    }
    return designation.stream().map(d -> new Designation()
        .setLanguage(d.getLanguage())
        .setName(d.getValue())
        .setDesignationKind("text")
        .setCaseSignificance(CaseSignificance.entire_term_case_insensitive)
        .setStatus(PublicationStatus.active)).collect(Collectors.toList());
  }

  private static List<ValueSetRuleFilter> mapRuleFilters(List<Filter> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return null;
    }
    return filters.stream().map(f -> {
      ValueSetRuleFilter filter = new ValueSetRuleFilter();
      filter.setProperty(new EntityProperty().setName(f.getProperty()));
      filter.setOperator(f.getOp());
      filter.setValue(f.getValue());
      return filter;
    }).collect(Collectors.toList());
  }

  private static List<ValueSetVersionConcept> mapConcepts(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    if (valueSet.getExpansion() == null || CollectionUtils.isEmpty(valueSet.getExpansion().getContains())) {
      return new ArrayList<>();
    }
    return valueSet.getExpansion().getContains().stream().map(c -> {
      ValueSetVersionConcept vsConcept = new ValueSetVersionConcept();
      vsConcept.setDisplay(new Designation()
          .setName(c.getDisplay())
          .setDesignationKind("text")
          .setCaseSignificance(CaseSignificance.entire_term_case_insensitive)
          .setStatus(PublicationStatus.active));
      vsConcept.setAdditionalDesignations(mapDesignations(c.getDesignation()));
      com.kodality.termserver.codesystem.Concept concept = new com.kodality.termserver.codesystem.Concept().setCode(c.getCode());
      concept.setCodeSystem(c.getSystem());
      vsConcept.setConcept(concept);
      return vsConcept;
    }).collect(Collectors.toList());
  }
}
