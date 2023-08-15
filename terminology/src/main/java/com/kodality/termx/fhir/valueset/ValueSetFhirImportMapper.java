package com.kodality.termx.fhir.valueset;


import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.ContactDetail.Telecom;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSet.ValueSetCopyright;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleType;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConcept;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeFilter;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ValueSetFhirImportMapper {

  public static ValueSet mapValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSet vs = new ValueSet();
    vs.setId(CodeSystemFhirMapper.parseCompositeId(valueSet.getId())[0]);
    vs.setUri(valueSet.getUrl());
    vs.setPublisher(valueSet.getPublisher());
    vs.setName(valueSet.getName());
    vs.setTitle(toLocalizedName(valueSet.getTitle()));
    vs.setDescription(toLocalizedName(valueSet.getDescription()));
    vs.setPurpose(toLocalizedName(valueSet.getPurpose()));
    vs.setNarrative(valueSet.getText() == null ? null : valueSet.getText().getDiv());
    vs.setIdentifiers(mapIdentifiers(valueSet.getIdentifier()));
    vs.setContacts(mapContacts(valueSet.getContact()));
    vs.setVersions(List.of(mapVersion(valueSet)));
    vs.setExperimental(valueSet.getExperimental());
    vs.setCopyright(new ValueSetCopyright().setHolder(valueSet.getCopyright()).setStatement(valueSet.getCopyrightLabel()));
    return vs;
  }

  private static LocalizedName toLocalizedName(String name) {
    if (name == null) {
      return null;
    }
    return new LocalizedName(Map.of(Language.en, name));
  }

  private static List<ContactDetail> mapContacts(List<com.kodality.zmei.fhir.datatypes.ContactDetail> details) {
    if (details == null) {
      return null;
    }
    return details.stream().map(d -> new ContactDetail()
            .setName(d.getName())
            .setTelecoms(d.getTelecom() == null ? null :
                d.getTelecom().stream().map(t -> new Telecom().setSystem(t.getSystem()).setUse(t.getUse()).setValue(t.getValue())).collect(Collectors.toList())))
        .collect(Collectors.toList());
  }

  private static List<Identifier> mapIdentifiers(List<com.kodality.zmei.fhir.datatypes.Identifier> identifiers) {
    if (identifiers == null) {
      return null;
    }
    return identifiers.stream().map(i -> new Identifier(i.getSystem(), i.getValue())).collect(Collectors.toList());
  }

  private static ValueSetVersion mapVersion(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSetVersion version = new ValueSetVersion();
    version.setValueSet(valueSet.getId());
    version.setVersion(valueSet.getVersion() == null ? "1.0.0" : valueSet.getVersion());
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(valueSet.getDate() == null ? LocalDate.now() : LocalDate.from(valueSet.getDate()));
    version.setRuleSet(mapRuleSet(valueSet));
    return version;
  }

  private static ValueSetVersionRuleSet mapRuleSet(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    if (valueSet.getCompose() == null) {
      return null;
    }
    ValueSetVersionRuleSet ruleSet = new ValueSetVersionRuleSet();
    ruleSet.setInactive(valueSet.getCompose().getInactive());
    if (valueSet.getCompose().getLockedDate() != null) {
      ruleSet.setLockedDate(valueSet.getCompose().getLockedDate().atStartOfDay().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }
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
      concept.setConcept(new ValueSetVersionConceptValue().setCode(c.getCode()));
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
