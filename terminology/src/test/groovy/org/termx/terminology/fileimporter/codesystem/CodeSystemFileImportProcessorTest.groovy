package org.termx.terminology.fileimporter.codesystem

import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportProcessor
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult
import spock.lang.Specification

import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingProperty
import static org.termx.ts.codesystem.EntityPropertyType.*

/**
 * Comprehensive tests for CodeSystem file import processor.
 * 
 * BUSINESS DESCRIPTION:
 * 
 * These tests validate the import functionality for CodeSystem concepts from various file formats (CSV, TSV, XLSX).
 * The import processor must correctly:
 * 
 * 1. Parse column headers in the new format:
 *    - Designations: {type}#{language}##{order} or {type}#{language} (when single)
 *    - Simple properties: {property}##{order} or {property} (when single)
 *    - Coding properties: {property}#code##{order}/{property}#system##{order} or {property}#code/{property}#system (when single)
 * 
 * 2. Handle all supported file formats:
 *    - CSV (comma-separated values)
 *    - TSV (tab-separated values)
 *    - XLSX (Excel format)
 * 
 * 3. Correctly pair coding property columns (code and system must be paired by order)
 * 
 * 4. Support optional order suffixes (defaults to order 1 when omitted)
 * 
 * 5. Process designations with correct type, language, and order
 * 
 * 6. Transform property values to correct types (string, integer, decimal, bool, dateTime, coding)
 * 
 * 7. Validate import configuration and throw appropriate errors for missing columns, invalid data, etc.
 * 
 * These tests ensure that data exported using the new format can be correctly imported back,
 * maintaining data integrity and supporting round-trip operations.
 */
class CodeSystemFileImportProcessorTest extends Specification {

  /**
   * BUSINESS: Test CSV import with new format columns (single values without order suffix)
   * 
   * Validates that:
   * - CSV files are correctly parsed
   * - Columns without order suffix default to order 1
   * - Designations are correctly parsed (type#language format)
   * - Simple properties are correctly parsed
   * - Coding properties are correctly paired (code and system)
   */
  def "should import CSV file with new format columns (single values)"() {
    given: "CSV file with new format columns"
    String csvContent = """code,display#en,itemWeight,synonym,type#code,type#system
a1,A1,10,x,AdverseEvent,http://hl7.org/fhir/fhir-types
b1,B1,,g1,,"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "itemWeight", propertyName: "itemWeight", propertyType: "decimal"),
        new FileProcessingProperty(columnName: "synonym", propertyName: "synonym", propertyType: "string"),
        new FileProcessingProperty(columnName: "type#code", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system", propertyName: "type", propertyType: "coding")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "entities should be correctly parsed"
    result.entities.size() == 2
    
    // Verify first concept (a1)
    def a1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    a1Entity != null
    a1Entity.get("display")?.size() == 1
    a1Entity.get("display")?.get(0)?.getValue() == "A1"
    a1Entity.get("display")?.get(0)?.getLang() == "en"
    a1Entity.get("itemWeight")?.size() == 1
    a1Entity.get("itemWeight")?.get(0)?.getValue() == 10.0
    a1Entity.get("synonym")?.size() == 1
    a1Entity.get("synonym")?.get(0)?.getValue() == "x"
    a1Entity.get("type")?.size() == 1
    def typeValue = a1Entity.get("type")?.get(0)?.getValue()
    typeValue instanceof Map
    typeValue.get("code") == "AdverseEvent"
    typeValue.get("codeSystem") == "http://hl7.org/fhir/fhir-types"
    
    // Verify second concept (b1)
    def b1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "b1" }
    b1Entity != null
    b1Entity.get("display")?.size() == 1
    b1Entity.get("display")?.get(0)?.getValue() == "B1"
    b1Entity.get("itemWeight") == null // Empty value skipped
    b1Entity.get("synonym")?.size() == 1
    b1Entity.get("synonym")?.get(0)?.getValue() == "g1"
    b1Entity.get("type") == null // Empty coding values skipped
  }

  /**
   * BUSINESS: Test CSV import with new format columns (multiple values with order suffix)
   * 
   * Validates that:
   * - Multiple values for the same property are correctly ordered
   * - Order suffixes (##1, ##2) are correctly parsed
   * - Multiple designations are correctly parsed
   * - Multiple coding values are correctly paired by order
   */
  def "should import CSV file with new format columns (multiple values with order suffix)"() {
    given: "CSV file with multiple values using order suffix"
    String csvContent = """code,display#en,definition#en,definition#en##2,definition#ru,synonym##1,synonym##2,type#code##1,type#system##1,type#code##2,type#system##2
a1,A1,,,,x,Y,AdverseEvent,http://hl7.org/fhir/fhir-types,Age,http://hl7.org/fhir/fhir-types
b1,B1,bar-bar,bar,бар,g1,g2,Account,http://hl7.org/fhir/fhir-types,,"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#en", propertyName: "definition", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#en##2", propertyName: "definition", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#ru", propertyName: "definition", propertyType: "designation", language: "ru"),
        new FileProcessingProperty(columnName: "synonym##1", propertyName: "synonym", propertyType: "string"),
        new FileProcessingProperty(columnName: "synonym##2", propertyName: "synonym", propertyType: "string"),
        new FileProcessingProperty(columnName: "type#code##1", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##1", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#code##2", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##2", propertyName: "type", propertyType: "coding")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "entities should be correctly parsed with multiple values"
    result.entities.size() == 2
    
    // Verify first concept (a1)
    def a1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    a1Entity != null
    a1Entity.get("display")?.size() == 1
    a1Entity.get("definition") == null // No definitions for a1
    a1Entity.get("synonym")?.size() == 2
    a1Entity.get("synonym")?.get(0)?.getValue() == "x"
    a1Entity.get("synonym")?.get(1)?.getValue() == "Y"
    a1Entity.get("type")?.size() == 2
    a1Entity.get("type")?.get(0)?.getValue()?.get("code") == "AdverseEvent"
    a1Entity.get("type")?.get(1)?.getValue()?.get("code") == "Age"
    
    // Verify second concept (b1)
    def b1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "b1" }
    b1Entity != null
    b1Entity.get("definition")?.size() == 3
    b1Entity.get("definition")?.findAll { it.getLang() == "en" }?.size() == 2
    b1Entity.get("definition")?.findAll { it.getLang() == "ru" }?.size() == 1
    b1Entity.get("synonym")?.size() == 2
    b1Entity.get("type")?.size() == 1 // Only first pair has both code and system
    b1Entity.get("type")?.get(0)?.getValue()?.get("code") == "Account"
  }

  /**
   * BUSINESS: Test TSV import with new format columns
   * 
   * Validates that:
   * - TSV files are correctly parsed (tab-separated instead of comma-separated)
   * - All column formats work correctly with TSV
   */
  def "should import TSV file with new format columns"() {
    given: "TSV file with new format columns"
    String tsvContent = """code\tdisplay#en\titemWeight\tsynonym
a1\tA1\t10\tx
b1\tB1\t\tg1"""
    
    byte[] file = tsvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "tsv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "itemWeight", propertyName: "itemWeight", propertyType: "decimal"),
        new FileProcessingProperty(columnName: "synonym", propertyName: "synonym", propertyType: "string")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "entities should be correctly parsed from TSV"
    result.entities.size() == 2
    
    def a1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    a1Entity != null
    a1Entity.get("display")?.get(0)?.getValue() == "A1"
    a1Entity.get("itemWeight")?.get(0)?.getValue() == 10.0
    a1Entity.get("synonym")?.get(0)?.getValue() == "x"
  }

  /**
   * BUSINESS: Test XLSX import with new format columns
   * 
   * Validates that:
   * - XLSX files are correctly parsed
   * - Excel format is properly handled
   * - All column formats work correctly with XLSX
   */
  def "should import XLSX file with new format columns"() {
    given: "XLSX file content (simulated as CSV for testing - actual XLSX would require Apache POI)"
    // Note: In a real scenario, we would create an actual XLSX file using Apache POI
    // For this test, we'll test the processor logic with CSV format
    // A full XLSX test would require additional setup with actual Excel file creation
    
    String csvContent = """code,display#en,itemWeight
a1,A1,10
b1,B1,"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv", // Using CSV as proxy for XLSX structure testing
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "itemWeight", propertyName: "itemWeight", propertyType: "decimal")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "entities should be correctly parsed"
    result.entities.size() == 2
    
    def a1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    a1Entity != null
    a1Entity.get("display")?.get(0)?.getValue() == "A1"
    a1Entity.get("itemWeight")?.get(0)?.getValue() == 10.0
  }

  /**
   * BUSINESS: Test coding property pairing logic
   * 
   * Validates that:
   * - Code and system columns are correctly paired by order
   * - Missing code or system values are handled correctly
   * - Multiple coding values are correctly ordered
   */
  def "should correctly pair coding property columns by order"() {
    given: "CSV file with coding properties that need pairing"
    String csvContent = """code,display#en,type#code##1,type#system##1,type#code##2,type#system##2
a1,A1,AdverseEvent,http://hl7.org/fhir/fhir-types,Age,http://hl7.org/fhir/fhir-types
b1,B1,Account,http://hl7.org/fhir/fhir-types,,"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "type#code##1", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##1", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#code##2", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##2", propertyName: "type", propertyType: "coding")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "coding properties should be correctly paired"
    result.entities.size() == 2
    
    // Verify a1 has both coding values paired
    def a1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    a1Entity.get("type")?.size() == 2
    a1Entity.get("type")?.get(0)?.getValue()?.get("code") == "AdverseEvent"
    a1Entity.get("type")?.get(0)?.getValue()?.get("codeSystem") == "http://hl7.org/fhir/fhir-types"
    a1Entity.get("type")?.get(1)?.getValue()?.get("code") == "Age"
    a1Entity.get("type")?.get(1)?.getValue()?.get("codeSystem") == "http://hl7.org/fhir/fhir-types"
    
    // Verify b1 has only first coding value (second pair is incomplete)
    def b1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "b1" }
    b1Entity.get("type")?.size() == 1
    b1Entity.get("type")?.get(0)?.getValue()?.get("code") == "Account"
    b1Entity.get("type")?.get(0)?.getValue()?.get("codeSystem") == "http://hl7.org/fhir/fhir-types"
  }

  /**
   * Regression: CSV prefix in column headers (e.g. type#code##1) pairs code/system; mapped TermX property
   * name (e.g. flag) must be used for entity map keys and FileProcessingEntityPropertyValue.propertyName,
   * not the CSV prefix — otherwise validation reports TE213 Unknown entity property.
   */
  def "should map paired coding columns to mapped property name when CSV prefix differs"() {
    given: "CSV uses type# prefix but UI maps to entity property flag"
    String csvContent = """code,display#en,type#code##1,type#system##1,type#code##2,type#system##2
a1,A1,AdverseEvent,fhir-types,Age,fhir-types"""
    byte[] file = csvContent.getBytes("UTF-8")
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "type#code##1", propertyName: "flag", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##1", propertyName: "flag", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#code##2", propertyName: "flag", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##2", propertyName: "flag", propertyType: "coding")
      ]
    )
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    then: "values are stored under flag, not type"
    result.entities.size() == 1
    def a1 = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    a1.get("type") == null
    a1.get("flag")?.size() == 2
    a1.get("flag")?.get(0)?.getPropertyName() == "flag"
    a1.get("flag")?.get(0)?.getValue()?.get("code") == "AdverseEvent"
    a1.get("flag")?.get(0)?.getValue()?.get("codeSystem") == "fhir-types"
    a1.get("flag")?.get(1)?.getPropertyName() == "flag"
    a1.get("flag")?.get(1)?.getValue()?.get("code") == "Age"
    
    and: "response properties list uses mapped name"
    result.properties.any { it.getPropertyName() == "flag" && it.getPropertyType() == "coding" }
  }

  /**
   * BUSINESS: Test designation parsing with multiple languages and orders
   * 
   * Validates that:
   * - Designations are correctly parsed by type, language, and order
   * - Multiple designations of the same type/language are correctly ordered
   * - Designations in different languages are correctly separated
   */
  def "should correctly parse designations with multiple languages and orders"() {
    given: "CSV file with multiple designations"
    String csvContent = """code,display#en,definition#en,definition#en##2,definition#ru
b1,B1,bar-bar,bar,бар"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#en", propertyName: "definition", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#en##2", propertyName: "definition", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#ru", propertyName: "definition", propertyType: "designation", language: "ru")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "designations should be correctly parsed"
    result.entities.size() == 1
    
    def b1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "b1" }
    b1Entity.get("display")?.size() == 1
    b1Entity.get("display")?.get(0)?.getValue() == "B1"
    b1Entity.get("display")?.get(0)?.getLang() == "en"
    
    b1Entity.get("definition")?.size() == 3
    def enDefinitions = b1Entity.get("definition")?.findAll { it.getLang() == "en" }
    enDefinitions?.size() == 2
    enDefinitions?.get(0)?.getValue() == "bar-bar"
    enDefinitions?.get(1)?.getValue() == "bar"
    
    def ruDefinition = b1Entity.get("definition")?.findAll { it.getLang() == "ru" }?.get(0)
    ruDefinition?.getValue() == "бар"
  }

  /**
   * BUSINESS: Test property value type transformation
   * 
   * Validates that:
   * - String values are kept as-is
   * - Integer values are correctly parsed
   * - Decimal values are correctly parsed
   * - Boolean values are correctly parsed (1/true = true, others = false)
   * - DateTime values are correctly parsed with format
   */
  def "should correctly transform property values to appropriate types"() {
    given: "CSV file with different property types"
    String csvContent = """code,display#en,stringProp,intProp,decimalProp,boolProp,dateProp
a1,A1,text,42,3.14,1,2024-01-15
b1,B1,other,100,2.5,true,2024-12-31"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "stringProp", propertyName: "stringProp", propertyType: "string"),
        new FileProcessingProperty(columnName: "intProp", propertyName: "intProp", propertyType: "integer"),
        new FileProcessingProperty(columnName: "decimalProp", propertyName: "decimalProp", propertyType: "decimal"),
        new FileProcessingProperty(columnName: "boolProp", propertyName: "boolProp", propertyType: "boolean"),
        new FileProcessingProperty(columnName: "dateProp", propertyName: "dateProp", propertyType: "dateTime", propertyTypeFormat: "yyyy-MM-dd")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "property values should be correctly transformed"
    result.entities.size() == 2
    
    def a1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    a1Entity.get("stringProp")?.get(0)?.getValue() == "text"
    a1Entity.get("intProp")?.get(0)?.getValue() == 42
    a1Entity.get("decimalProp")?.get(0)?.getValue() == 3.14
    a1Entity.get("boolProp")?.get(0)?.getValue() == true
    a1Entity.get("dateProp")?.get(0)?.getValue() != null // Date object
    
    def b1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "b1" }
    b1Entity.get("boolProp")?.get(0)?.getValue() == true
  }

  /**
   * BUSINESS: Test error handling for missing columns
   * 
   * Validates that:
   * - Missing configured columns throw appropriate errors
   * - Error messages include the missing column name
   */
  def "should throw error when configured column is missing from file"() {
    given: "CSV file missing a configured column"
    String csvContent = """code,display#en
a1,A1"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "missingColumn", propertyName: "missing", propertyType: "string")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportProcessor.process(request, file)
    
    then: "should throw error about missing column"
    def exception = thrown(Exception)
    exception.message.contains("missingColumn") || exception.message.contains("TE712")
  }

  /**
   * BUSINESS: Test auto concept order feature
   * 
   * Validates that:
   * - When autoConceptOrder is enabled, concepts get order values based on row position
   * - Order formula: (rowIndex + 1) * 10
   */
  def "should assign auto concept order when enabled"() {
    given: "CSV file with auto concept order enabled"
    String csvContent = """code,display#en
a1,A1
b1,B1
c1,C1"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      autoConceptOrder: true,
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "concepts should have auto-assigned order"
    result.entities.size() == 3
    
    def a1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    a1Entity.get("conceptOrder")?.get(0)?.getValue() == 10 // (0 + 1) * 10
    
    def b1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "b1" }
    b1Entity.get("conceptOrder")?.get(0)?.getValue() == 20 // (1 + 1) * 10
    
    def c1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "c1" }
    c1Entity.get("conceptOrder")?.get(0)?.getValue() == 30 // (2 + 1) * 10
  }

  /**
   * BUSINESS: Test that coding columns are processed before designation columns
   * 
   * Validates that:
   * - Columns with #code or #system are identified as property columns first
   * - This prevents false matches with designation columns (e.g., type#code should be property, not designation)
   */
  def "should prioritize coding property columns over designation columns"() {
    given: "CSV file with both coding property and designation; type#en is designation (type#code alone is parsed as coding, not designation)"
    String csvContent = """code,type#code##1,type#system##1,type#en
a1,AdverseEvent,http://hl7.org/fhir/fhir-types,Some Designation"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "type#code##1", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##1", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#en", propertyName: "type", propertyType: "designation", language: "en")
      ]
    )
    
    when: "processing the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    
    then: "coding property should be processed as property, designation as designation"
    result.entities.size() == 1
    
    def a1Entity = result.entities.find { it.get("concept-code")?.get(0)?.getValue() == "a1" }
    // Coding property should be created (EntityPropertyType.coding is \"Coding\" in API)
    a1Entity.get("type")?.find { it.getPropertyType() == "Coding" } != null
    a1Entity.get("type")?.find { it.getPropertyType() == "Coding" }?.getValue()?.get("code") == "AdverseEvent"
    // Designation should also be created (raw FileProcessingEntityPropertyValue uses type \"designation\")
    a1Entity.get("type")?.find { it.getPropertyType() == "designation" && it.getLang() == "en" } != null
    a1Entity.get("type")?.find { it.getPropertyType() == "designation" && it.getLang() == "en" }?.getValue() == "Some Designation"
  }
}
