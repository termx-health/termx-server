package com.kodality.termx.fileimporter.valueset.utils;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.fileimporter.valueset.utils.ValueSetFileImportRequest.FileProcessingValueSet;
import com.kodality.termx.fileimporter.valueset.utils.ValueSetFileImportRequest.FileProcessingValueSetVersion;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleType;
import java.time.LocalDate;
import java.util.List;

public class ValueSetFileImportMapper {
  public static final String OID_SYSTEM = "urn:ietf:rfc:3986";
  public static final String OID_PREFIX = "urn:oid:";


  public static ValueSet toValueSet(FileProcessingValueSet fpValueSet, FileProcessingValueSetVersion fpVersion, List<ValueSetVersionConcept> concepts,
                                    ValueSet existingValueSet, ValueSetVersion existingValueSetVersion, ValueSetVersionRule existingRule) {
    ValueSet valueSet = existingValueSet != null ? JsonUtil.fromJson(JsonUtil.toJson(existingValueSet), ValueSet.class) : new ValueSet();
    valueSet.setId(fpValueSet.getId());
    valueSet.setUri(fpValueSet.getUri() != null ? fpValueSet.getUri() : valueSet.getUri());
    valueSet.setName(fpValueSet.getName() != null ? fpValueSet.getName() : valueSet.getName());
    valueSet.setIdentifiers(fpValueSet.getOid() != null ? List.of(new Identifier(OID_SYSTEM, OID_PREFIX + fpValueSet.getOid())) : valueSet.getIdentifiers());
    valueSet.setTitle(fpValueSet.getTitle() != null ? fpValueSet.getTitle() : valueSet.getTitle());
    valueSet.setDescription(fpValueSet.getDescription() != null ? fpValueSet.getDescription() : valueSet.getDescription());
    valueSet.setVersions(List.of(toVsVersion(fpVersion, concepts, fpValueSet.getId(), existingValueSetVersion, existingRule)));
    return valueSet;
  }

  private static ValueSetVersion toVsVersion(FileProcessingValueSetVersion fpVersion, List<ValueSetVersionConcept> concepts, String valueSet,
                                             ValueSetVersion existingValueSetVersion, ValueSetVersionRule existingRule) {
    ValueSetVersion version = existingValueSetVersion != null ? JsonUtil.fromJson(JsonUtil.toJson(existingValueSetVersion), ValueSetVersion.class) : new ValueSetVersion();
    version.setValueSet(valueSet);
    version.setVersion(fpVersion.getNumber());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(fpVersion.getReleaseDate() == null ? LocalDate.now() : fpVersion.getReleaseDate());
    version.setPreferredLanguage(SessionStore.require().getLang());
    version.setRuleSet(version.getRuleSet() == null ? new ValueSetVersionRuleSet() : version.getRuleSet());
    version.getRuleSet().setRules(existingRule != null ? List.of(existingRule) : List.of(new ValueSetVersionRule()
        .setType(ValueSetVersionRuleType.include)
        .setCodeSystem(fpVersion.getRule().getCodeSystem())
        .setCodeSystemVersion(new CodeSystemVersionReference().setId(fpVersion.getRule().getCodeSystemVersionId()))));
    version.getRuleSet().getRules().get(0).setId(null);
    version.getRuleSet().getRules().get(0).setConcepts(concepts);
    return version;
  }

}
