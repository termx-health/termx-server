package org.termx

import com.kodality.commons.model.LocalizedName
import org.termx.editionest.icd10est.utils.Icd10Est
import org.termx.editionest.icd10est.utils.Icd10EstMapper
import org.termx.ts.codesystem.CodeSystemImportConfiguration
import spock.lang.Specification

import java.time.LocalDate

/**
 * Migrated from tehik fork (ICD10-EST import fix, commits 234a750e / 58ea7d2c): a subsection's
 * dagger/asterisk postfix &lt;sub&gt; codes are appended to each leaf item as child concepts
 * (item code + the suffix after '*'), rather than being discarded.
 *
 * NOTE: this is a SYNTHETIC test. The shipped fixture `icd10-est.xml` contains no &lt;sub&gt;
 * postfix elements, so this encodes the fork's algorithm rather than real Estonian source data.
 * It should be re-grounded against a real ICD10-EST XML sample carrying dagger/asterisk codes
 * before the exact code-composition rule (`split("*")`, `parent.code + suffix`) is fully trusted.
 */
class Icd10EstPostfixMapperSpec extends Specification {

  def config = new CodeSystemImportConfiguration(
      uri: 'https://pub.e-tervis.ee/classifications/RHK-10/8',
      publisher: 'Ministry of Social Affairs of Estonia',
      version: '8',
      validFrom: LocalDate.of(2022, 1, 1),
      codeSystem: 'icd-10-est',
      codeSystemName: new LocalizedName(Map.of("et", "RHK-10")),
      codeSystemDescription: new LocalizedName(Map.of("et", "RHK-10")))

  private static Icd10Est.Obj obj(String est) {
    new Icd10Est.Obj(hidden: 1, nameEst: est)
  }

  def "postfix subs on a subsection are appended to leaf item codes"() {
    given: "a subsection carries a postfix Sub '*.1'; its leaf item 'A02' has no subs of its own"
    def postfix = new Icd10Est.Sub(code: "*.1", object: [obj("Postfix name")])
    def item = new Icd10Est.Item(code: "A02", object: [obj("Item name")])
    def subsection = new Icd10Est.SubSection(code: "A00-A09", object: [obj("SubSection")], children: [item], sub: [postfix])
    def section = new Icd10Est.Section(code: "A00-B99", object: [obj("Section")], children: [subsection])
    def chapter = new Icd10Est.Chapter(code: "I", object: [obj("Chapter")], children: [section])
    def icd = new Icd10Est(chapter: chapter)

    when:
    def concepts = Icd10EstMapper.toRequest(config, [icd]).concepts

    then: "a postfix concept 'A02.1' (leaf item code + the suffix after '*') is produced"
    def postfixConcept = concepts.find { it.code == "A02.1" }
    postfixConcept != null
    postfixConcept.designations*.name.contains("Postfix name")
    postfixConcept.associations*.targetCode == ["A02"]
  }
}
