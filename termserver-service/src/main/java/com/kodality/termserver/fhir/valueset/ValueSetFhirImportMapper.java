package com.kodality.termserver.fhir.valueset;


import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.ContactDetail;
import com.kodality.termserver.ContactDetail.Telecom;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetRuleSet;
import com.kodality.termserver.valueset.ValueSetRuleSet.ValueSetRule;
import com.kodality.termserver.valueset.ValueSetRuleSet.ValueSetRule.ValueSetRuleFilter;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersion.ValueSetConcept;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Concept;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Filter;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.Include;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
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
    return version;
  }

  private static ValueSetRuleSet mapRuleSet(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    if (valueSet.getCompose() == null) {
      return null;
    }
    ValueSetRuleSet ruleSet = new ValueSetRuleSet();
    ruleSet.setInactive(valueSet.getCompose().getInactive());
    ruleSet.setLockedDate(valueSet.getCompose().getLockedDate());
    ruleSet.setIncludeRules(mapRules(valueSet.getCompose().getInclude()));
    ruleSet.setExcludeRules(mapRules(valueSet.getCompose().getExclude()));
    return ruleSet;
  }

  private static List<ValueSetRule> mapRules(List<Include> include) {
    if (CollectionUtils.isEmpty(include)) {
      return null;
    }
    return include.stream().map(inc -> {
      ValueSetRule rule = new ValueSetRule();
      rule.setCodeSystem(inc.getSystem());
      rule.setCodeSystemVersion(inc.getVersion());
      rule.setConcepts(mapRuleConcepts(inc.getConcept()));
      rule.setFilters(mapRuleFilters(inc.getFilter()));
      rule.setValueSet(inc.getValueSet());
      return rule;
    }).collect(Collectors.toList());
  }

  private static List<ValueSetConcept> mapRuleConcepts(List<Concept> concepts) {
    if (CollectionUtils.isEmpty(concepts)) {
      return null;
    }
    return concepts.stream().map(c -> {
      ValueSetConcept concept = new ValueSetConcept();
      concept.setConcept(new com.kodality.termserver.codesystem.Concept().setCode(c.getCode()));
      concept.setDisplay(new Designation().setName(c.getDisplay()));
      concept.setAdditionalDesignations(mapDesignations(c.getDesignation()));
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
        .setDesignationKind("text")).collect(Collectors.toList());
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
}
