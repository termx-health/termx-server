package org.termx.terminology.terminology.valueset.expansion;

import com.kodality.zmei.fhir.FhirMapper;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.terminology.terminology.valueset.ValueSetService;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import org.termx.terminology.fhir.valueset.ValueSetFhirMapper;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.valueset.ValueSet;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionReference;
import org.termx.ts.valueset.ValueSetVersionRuleSet;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;

@Singleton
@RequiredArgsConstructor
public class ValueSetRuleExpandService {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetVersionConceptRepository valueSetVersionConceptRepository;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final ValueSetFhirMapper valueSetFhirMapper;

  public List<ValueSetVersionConcept> expandRule(String valueSetId, String valueSetVersionId, ValueSetVersionRule rule, boolean inactiveConcepts) {
    ValueSet valueSet = valueSetService.load(valueSetId);
    ValueSetVersion sourceVersion = valueSetVersionId == null ? valueSetVersionService.loadLastVersion(valueSetId) :
        valueSetVersionService.load(valueSetId, valueSetVersionId).orElse(null);
    if (valueSet == null || sourceVersion == null || rule == null) {
      return List.of();
    }

    ValueSetVersion version = new ValueSetVersion();
    version.setId(sourceVersion.getId());
    version.setValueSet(sourceVersion.getValueSet());
    version.setVersion(sourceVersion.getVersion());
    version.setStatus(sourceVersion.getStatus());
    version.setReleaseDate(sourceVersion.getReleaseDate());
    version.setExpirationDate(sourceVersion.getExpirationDate());
    version.setAlgorithm(sourceVersion.getAlgorithm());
    version.setPreferredLanguage(sourceVersion.getPreferredLanguage());
    version.setSupportedLanguages(sourceVersion.getSupportedLanguages());
    version.setRuleSet(new ValueSetVersionRuleSet()
        .setInactive(inactiveConcepts)
        .setRules(List.of(enrichRule(rule))));

    String inlineValueSetJson = FhirMapper.toJson(valueSetFhirMapper.toFhir(valueSet, version, List.of()));
    List<ValueSetVersionConcept> expansion = valueSetVersionConceptRepository.expandFromJson(inlineValueSetJson);
    List<ValueSetVersionConcept> decorated = valueSetVersionConceptService.decorate(expansion, version, version.getPreferredLanguage());
    return inactiveConcepts ? decorated : decorated.stream().filter(ValueSetVersionConcept::isActive).toList();
  }

  private ValueSetVersionRule enrichRule(ValueSetVersionRule source) {
    ValueSetVersionRule rule = new ValueSetVersionRule()
        .setId(source.getId())
        .setType(source.getType())
        .setCodeSystem(source.getCodeSystem())
        .setCodeSystemVersion(copyCodeSystemVersion(source.getCodeSystemVersion()))
        .setProperties(source.getProperties())
        .setConcepts(source.getConcepts())
        .setFilters(source.getFilters())
        .setValueSet(source.getValueSet())
        .setValueSetVersion(copyValueSetVersion(source.getValueSetVersion()))
        .setCodeSystemUri(source.getCodeSystemUri())
        .setCodeSystemBaseUri(source.getCodeSystemBaseUri())
        .setValueSetUri(source.getValueSetUri());

    if (rule.getCodeSystem() != null) {
      CodeSystem codeSystem = codeSystemService.load(rule.getCodeSystem()).orElse(null);
      if (codeSystem != null) {
        rule.setCodeSystemUri(codeSystem.getUri());
        rule.setCodeSystemBaseUri(codeSystem.getBaseCodeSystemUri());
      }
      if (rule.getCodeSystemVersion() != null && (rule.getCodeSystemVersion().getUri() == null || rule.getCodeSystemVersion().getBaseCodeSystemVersion() == null)) {
        CodeSystemVersion codeSystemVersion = loadCodeSystemVersion(rule.getCodeSystem(), rule.getCodeSystemVersion());
        if (codeSystemVersion != null) {
          rule.setCodeSystemVersion(codeSystemVersion);
        }
      }
    }

    if (rule.getValueSet() != null && rule.getValueSetUri() == null) {
      ValueSet valueSet = valueSetService.load(rule.getValueSet());
      if (valueSet != null) {
        rule.setValueSetUri(valueSet.getUri());
      }
    }
    return rule;
  }

  private CodeSystemVersion loadCodeSystemVersion(String codeSystemId, CodeSystemVersionReference versionRef) {
    if (versionRef == null || codeSystemId == null) {
      return null;
    }
    if (versionRef.getVersion() != null) {
      return codeSystemVersionService.load(codeSystemId, versionRef.getVersion()).orElse(null);
    }
    if (versionRef.getId() != null) {
      return codeSystemVersionService.load(versionRef.getId());
    }
    return null;
  }

  private CodeSystemVersionReference copyCodeSystemVersion(CodeSystemVersionReference source) {
    if (source == null) {
      return null;
    }
    return new CodeSystemVersionReference()
        .setId(source.getId())
        .setVersion(source.getVersion())
        .setUri(source.getUri())
        .setStatus(source.getStatus())
        .setPreferredLanguage(source.getPreferredLanguage())
        .setReleaseDate(source.getReleaseDate())
        .setBaseCodeSystemVersion(copyCodeSystemVersion(source.getBaseCodeSystemVersion()));
  }

  private ValueSetVersionReference copyValueSetVersion(ValueSetVersionReference source) {
    if (source == null) {
      return null;
    }
    return new ValueSetVersionReference()
        .setId(source.getId())
        .setVersion(source.getVersion());
  }
}
