package org.termx.terminology.fileimporter.valueset

import org.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportMapper
import org.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportRequest.FileProcessingValueSet
import org.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportRequest.FileProcessingValueSetVersion
import org.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportRequest.FileProcessingValueSetVersionRule
import org.termx.ts.PublicationStatus
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification

/**
 * A file import into an existing version cancels that version together with its rule set and saves a new one.
 * The mapped version is a copy of the existing one, so it must not carry the cancelled rule set's id: the id
 * belongs to another version and the rule set save refuses it.
 */
class ValueSetFileImportMapperSpec extends Specification {

  def "a version mapped over an existing one carries no ids of the rule set and rule it replaces"() {
    given:
    def existingRule = new ValueSetVersionRule(id: 11L, type: "include", codeSystem: "cs")
    def existing = new ValueSetVersion(id: 5L, valueSet: "vs", version: "1.0", status: PublicationStatus.draft,
        ruleSet: new ValueSetVersionRuleSet(id: 10L, rules: [existingRule]))
    def fpValueSet = new FileProcessingValueSet(id: "vs")
    def fpVersion = new FileProcessingValueSetVersion(number: "1.0", language: "en", inactive: false, rule: new FileProcessingValueSetVersionRule(id: 11L))

    when:
    def version = ValueSetFileImportMapper.toValueSet(fpValueSet, fpVersion, [], null, existing, existingRule).versions[0]

    then:
    version.ruleSet.id == null
    version.ruleSet.rules*.id == [null]
  }
}
