package org.termx.terminology.terminology.codesystem

import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.Designation
import org.termx.ts.codesystem.EntityProperty
import org.termx.ts.codesystem.EntityPropertyType
import org.termx.ts.codesystem.EntityPropertyValue
import spock.lang.Specification
import spock.lang.Unroll

import java.text.SimpleDateFormat

/**
 * The content signature must normalize values the SAME way on both sides (parsed import vs reloaded
 * DB), so formatting-only differences (decimal scale, date/dateTime shape, number-vs-string,
 * coding extra fields) are NOT seen as a change.
 */
class ConceptContentSignatureTest extends Specification {

  @Unroll
  def "normalize(#type): '#a' and '#b' are equal -> #expected"() {
    expect:
    (ConceptContentSignature.normalize(a, type) == ConceptContentSignature.normalize(b, type)) == expected

    where:
    type                          | a                                  | b                          || expected
    EntityPropertyType.decimal    | 2.5d                               | new BigDecimal("2.50")     || true
    EntityPropertyType.decimal    | "2.50"                             | 2.5d                       || true
    EntityPropertyType.decimal    | new BigDecimal("0.00")             | 0d                         || true
    EntityPropertyType.decimal    | 100d                               | new BigDecimal("100.00")   || true
    EntityPropertyType.decimal    | 2.5d                               | 2.6d                       || false
    EntityPropertyType.integer    | 5                                  | "5"                        || true
    EntityPropertyType.integer    | 5                                  | 5.0d                       || true
    EntityPropertyType.integer    | 5                                  | 6                          || false
    EntityPropertyType.bool       | true                               | "true"                     || true
    EntityPropertyType.bool       | true                               | "1"                        || true
    EntityPropertyType.bool       | false                              | "0"                        || true
    EntityPropertyType.bool       | true                               | false                      || false
    EntityPropertyType.string     | " Apple "                          | "Apple"                    || true
    EntityPropertyType.string     | "Apple"                            | "apple"                    || false
  }

  def "dateTime: a parsed Date and the equivalent ISO instant string are equal"() {
    given: "import side parses '2026-06-08' with a yyyy-MM-dd format -> java.util.Date (UTC midnight)"
    def fmt = new SimpleDateFormat("yyyy-MM-dd"); fmt.setTimeZone(TimeZone.getTimeZone("UTC"))
    def importDate = fmt.parse("2026-06-08")

    expect:
    ConceptContentSignature.normalize(importDate, EntityPropertyType.dateTime) ==
        ConceptContentSignature.normalize("2026-06-08T00:00:00Z", EntityPropertyType.dateTime)
    and: "epoch millis of the same instant also matches"
    ConceptContentSignature.normalize(importDate.getTime(), EntityPropertyType.dateTime) ==
        ConceptContentSignature.normalize("2026-06-08T00:00:00Z", EntityPropertyType.dateTime)
    and: "a different day differs"
    ConceptContentSignature.normalize("2026-06-09", EntityPropertyType.dateTime) !=
        ConceptContentSignature.normalize("2026-06-08T00:00:00Z", EntityPropertyType.dateTime)
  }

  def "coding: file Map{code,system} and stored EntityPropertyValueCodingValue compare by code|system"() {
    given:
    def fileValue = [code: "X", codeSystem: "http://sys"]
    def dbValue = new EntityPropertyValue.EntityPropertyValueCodingValue("X", "http://sys")
    dbValue.setDisplay("Some display ignored for equality")

    expect:
    ConceptContentSignature.normalize(fileValue, EntityPropertyType.coding) ==
        ConceptContentSignature.normalize(dbValue, EntityPropertyType.coding)
  }

  def "same content with only formatting differences (same order) produces the same signature"() {
    given:
    def props = [
        (1L): new EntityProperty().setId(1L).setName("dose").setType(EntityPropertyType.decimal),
        (2L): new EntityProperty().setId(2L).setName("active").setType(EntityPropertyType.bool),
    ]
    def imported = new CodeSystemEntityVersion().setCode("A")
        .setDesignations([designation("Apple", "en"), designation("Apfel", "de")])
        .setPropertyValues([propertyValue(1L, 2.5d), propertyValue(2L, "1")])
    def stored = new CodeSystemEntityVersion().setCode("A")
        .setDesignations([designation("Apple", "en"), designation("Apfel", "de")])
        // same order, different value formatting
        .setPropertyValues([propertyValue(1L, new BigDecimal("2.50")), propertyValue(2L, true)])

    expect:
    ConceptContentSignature.sameContent(imported, stored, props)
  }

  def "designation type is compared by CODE, not internal id"() {
    given: "import carries the type code only; the DB row carries code + resolved id"
    def imported = new CodeSystemEntityVersion().setCode("A").setDesignations([
        new Designation().setName("Apple").setLanguage("en").setDesignationType("display")])
    def stored = new CodeSystemEntityVersion().setCode("A").setDesignations([
        new Designation().setName("Apple").setLanguage("en").setDesignationType("display").setDesignationTypeId(10L)])

    expect:
    ConceptContentSignature.sameContent(imported, stored, [:])
  }

  def "reordering designations IS a change (order is significant)"() {
    given:
    def a = new CodeSystemEntityVersion().setCode("A").setDesignations([designation("Apple", "en"), designation("Apfel", "de")])
    def b = new CodeSystemEntityVersion().setCode("A").setDesignations([designation("Apfel", "de"), designation("Apple", "en")])

    expect:
    !ConceptContentSignature.sameContent(a, b, [:])
  }

  def "a real change is detected"() {
    given:
    def props = [(1L): new EntityProperty().setId(1L).setName("dose").setType(EntityPropertyType.decimal)]
    def a = new CodeSystemEntityVersion().setCode("A").setPropertyValues([propertyValue(1L, 2.5d)])
    def b = new CodeSystemEntityVersion().setCode("A").setPropertyValues([propertyValue(1L, 3.0d)])

    expect:
    !ConceptContentSignature.sameContent(a, b, props)
  }

  private static Designation designation(String name, String lang) {
    return new Designation().setName(name).setLanguage(lang).setDesignationType("display").setDesignationTypeId(10L)
  }

  private static EntityPropertyValue propertyValue(Long propertyId, Object value) {
    return new EntityPropertyValue().setEntityPropertyId(propertyId).setValue(value)
  }
}
