package com.kodality.termserver.fhir.valueset;


import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.ContactDetail;
import com.kodality.termserver.ts.ContactDetail.Telecom;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConcept;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeFilter;
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
    version.setReleaseDate(valueSet.getDate() == null ? LocalDate.now() : LocalDate.from(valueSet.getDate()));
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
  private static List<ValueSetVersionRule> mapRules(List<ValueSetComposeInclude> include, List<ValueSetComposeInclude> exclude) {
    List<ValueSetVersionRule> rules = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(include)) {
      rules.addAll(include.stream().map(inc -> mapRule(inc, ValueSetVersionRuleType.include)).toList());
    }
    if (CollectionUtils.isNotEmpty(exclude)) {
      rules.addAll(exclude.stream().map(exc -> mapRule(exc, ValueSetVersionRuleType.exclude)).toList());
    }
    return rules;
  }

  private static ValueSetVersionRule mapRule(ValueSetComposeInclude r, String type) {
    ValueSetVersionRule rule = new ValueSetVersionRule();
    rule.setType(type);
    rule.setCodeSystem(r.getSystem());
    rule.setConcepts(mapRuleConcepts(r.getConcept()));
    rule.setFilters(mapRuleFilters(r.getFilter()));
    rule.setValueSet(r.getValueSet());
    return rule;
  }

  private static List<ValueSetVersionConcept> mapRuleConcepts(List<ValueSetComposeIncludeConcept> concepts) {
    if (CollectionUtils.isEmpty(concepts)) {
      return null;
    }
    return concepts.stream().map(c -> {
      ValueSetVersionConcept concept = new ValueSetVersionConcept();
      concept.setConcept(new Concept().setCode(c.getCode()));
      concept.setDisplay(c.getDisplay() != null ? new Designation().setName(c.getDisplay()) : null);
      concept.setAdditionalDesignations(mapDesignations(c.getDesignation()));
      return concept;
    }).collect(Collectors.toList());
  }

  private static List<Designation> mapDesignations(List<ValueSetComposeIncludeConceptDesignation> designation) {
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

  private static List<ValueSetRuleFilter> mapRuleFilters(List<ValueSetComposeIncludeFilter> filters) {
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
      Concept concept = new Concept().setCode(c.getCode());
      concept.setCodeSystem(c.getSystem());
      vsConcept.setConcept(concept);
      return vsConcept;
    }).collect(Collectors.toList());
  }
}
