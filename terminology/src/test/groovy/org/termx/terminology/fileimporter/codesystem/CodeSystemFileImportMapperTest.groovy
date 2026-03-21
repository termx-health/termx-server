package org.termx.terminology.fileimporter.codesystem

import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportMapper
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportProcessor
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemAssociation
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.Designation
import org.termx.ts.codesystem.EntityProperty
import org.termx.ts.codesystem.EntityPropertyValue
import spock.lang.Specification

import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystem
import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystemVersion
import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingProperty
import static org.termx.ts.codesystem.EntityPropertyType.*

/**
 * Comprehensive tests for CodeSystem file import mapper.
 * 
 * BUSINESS DESCRIPTION:
 * 
 * These tests validate the mapping functionality from parsed import file data to CodeSystem domain objects.
 * The mapper transforms intermediate FileProcessingEntityPropertyValue objects into actual CodeSystem entities:
 * - Concept objects with code and description
 * - CodeSystemEntityVersion objects with status
 * - Designation objects with type, language, status, preferred flag
 * - EntityPropertyValue objects for non-designation properties
 * - CodeSystemAssociation objects for hierarchical relationships
 * 
 * These tests ensure that data imported from files is correctly mapped to the domain model,
 * maintaining data integrity and supporting real-world migration scenarios.
 */
class CodeSystemFileImportMapperTest extends Specification {

  /**
   * BUSINESS: Test basic concept mapping with display designation (Estonian migration pattern)
   * 
   * Inspired by: vastsyndinu-ajalisus from pub_resources.csv (Kood=concept-code, Nimetus=display|et)
   * 
   * Validates that:
   * - Concept.code is correctly set from concept-code property
   * - Display designation is created with correct type, language, preferred flag
   * - Designation status is set to active
   * - Case significance is set correctly
   */
  def "should map basic concept with display designation"() {
    given: "CSV file with code and display columns (legacy format)"
    String csvContent = """code,Nimetus
a1,A1
b1,B1"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "et"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "Nimetus", propertyName: "display", propertyType: "designation", language: "et")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "concepts should be correctly mapped"
    codeSystem.concepts.size() == 2
    
    def a1Concept = codeSystem.concepts.find { it.code == "a1" }
    a1Concept != null
    a1Concept.code == "a1"
    a1Concept.versions.size() == 1
    
    def a1Version = a1Concept.versions[0]
    a1Version.code == "a1"
    a1Version.designations.size() == 1
    
    def a1Display = a1Version.designations[0]
    a1Display.name == "A1"
    a1Display.language == "et"
    a1Display.designationType == "display"
    a1Display.preferred == true
    a1Display.status == PublicationStatus.active
    a1Display.caseSignificance == "ci"
  }

  /**
   * BUSINESS: Test concept mapping with parent hierarchy association
   * 
   * Inspired by: immuniseerimise-korvalnahud (Vanem_kood=parent)
   * 
   * Validates that:
   * - Parent association is created with type 'is-a'
   * - Association targetCode is set to parent code
   * - Association status is set to active
   */
  def "should map concept with parent hierarchy association"() {
    given: "CSV file with code, display, and parent columns"
    String csvContent = """code,display#en,Vanem_kood
1,Parent Concept,
101,Child Concept,1"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "Vanem_kood", propertyName: "parent", propertyType: "string")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "concepts should have correct associations"
    codeSystem.concepts.size() == 2
    
    def childConcept = codeSystem.concepts.find { it.code == "101" }
    childConcept != null
    childConcept.versions[0].associations.size() == 1
    
    def association = childConcept.versions[0].associations[0]
    association.associationType == "is-a"
    association.targetCode == "1"
    association.status == PublicationStatus.active
  }

  /**
   * BUSINESS: Test concept mapping with definition designation
   * 
   * Inspired by: poordumise-erakorralisus (Pikk_nimetus=definition|et, Nimetus=display|et)
   * 
   * Validates that:
   * - Multiple designations are created (display and definition)
   * - Display designation has preferred=true
   * - Definition designation has preferred=false
   * - Both designations have correct type and language
   */
  def "should map concept with definition designation"() {
    given: "CSV file with display and definition columns"
    String csvContent = """code,Nimetus,Pikk_nimetus
b1,B1,bar-bar"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "et"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "Nimetus", propertyName: "display", propertyType: "designation", language: "et"),
        new FileProcessingProperty(columnName: "Pikk_nimetus", propertyName: "definition", propertyType: "designation", language: "et")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "concept should have both display and definition designations"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    concept.versions[0].designations.size() == 2
    
    def display = concept.versions[0].designations.find { it.designationType == "display" }
    display != null
    display.name == "B1"
    display.preferred == true
    
    def definition = concept.versions[0].designations.find { it.designationType == "definition" }
    definition != null
    definition.name == "bar-bar"
    definition.preferred == false
  }

  /**
   * BUSINESS: Test concept mapping with custom string property
   * 
   * Inspired by: syndimiskoht (Selgitus=comment)
   * 
   * Validates that:
   * - Custom string property is mapped to EntityPropertyValue
   * - Property value is correctly stored
   * - Property is not treated as designation or association
   */
  def "should map concept with custom string property"() {
    given: "CSV file with code, display, and comment columns"
    String csvContent = """code,display#en,Selgitus
a1,A1,Some comment text"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "Selgitus", propertyName: "comment", propertyType: "string")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "concept should have comment property value"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    def propertyValues = concept.versions[0].propertyValues
    propertyValues.size() == 1
    
    def commentProp = propertyValues.find { it.entityProperty == "comment" }
    commentProp != null
    commentProp.value == "Some comment text"
  }

  /**
   * BUSINESS: Test coding property mapping (new format)
   * 
   * Validates that:
   * - Coding properties with code/system pairs are correctly mapped
   * - Multiple coding values are correctly ordered
   * - Coding value contains both code and codeSystem
   */
  def "should map coding property with new format"() {
    given: "CSV file with coding properties in new format"
    String csvContent = """code,display#en,type#code##1,type#system##1,type#code##2,type#system##2
a1,A1,AdverseEvent,http://hl7.org/fhir/fhir-types,Age,http://hl7.org/fhir/fhir-types"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "type#code##1", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##1", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#code##2", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system##2", propertyName: "type", propertyType: "coding")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "concept should have coding property values"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    def propertyValues = concept.versions[0].propertyValues
    propertyValues.size() == 2
    
    def type1 = propertyValues[0].asCodingValue()
    type1.code == "AdverseEvent"
    type1.codeSystem == "http://hl7.org/fhir/fhir-types"
    
    def type2 = propertyValues[1].asCodingValue()
    type2.code == "Age"
    type2.codeSystem == "http://hl7.org/fhir/fhir-types"
  }

  /**
   * BUSINESS: Test multiple designations mapping (new format)
   * 
   * Validates that:
   * - Multiple designations of same type/language are correctly ordered
   * - Designations in different languages are correctly separated
   * - Display designation is preferred
   */
  def "should map multiple designations with new format"() {
    given: "CSV file with multiple designations"
    String csvContent = """code,display#en,definition#en,definition#en##2,definition#ru
b1,B1,bar-bar,bar,бар"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#en", propertyName: "definition", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#en##2", propertyName: "definition", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#ru", propertyName: "definition", propertyType: "designation", language: "ru")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "concept should have multiple designations"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    concept.versions[0].designations.size() == 4
    
    def display = concept.versions[0].designations.find { it.designationType == "display" }
    display.name == "B1"
    display.preferred == true
    
    def enDefinitions = concept.versions[0].designations.findAll { it.designationType == "definition" && it.language == "en" }
    enDefinitions.size() == 2
    enDefinitions*.name.containsAll(["bar-bar", "bar"])
    
    def ruDefinition = concept.versions[0].designations.find { it.designationType == "definition" && it.language == "ru" }
    ruDefinition.name == "бар"
  }

  /**
   * BUSINESS: Test status mapping with various formats
   * 
   * Validates that:
   * - Status values are correctly mapped via PublicationStatus.getStatus()
   * - Various status formats (1, 0, A, active, retired) are handled
   */
  def "should map status with various formats"() {
    given: "CSV file with status column containing different formats"
    String csvContent = """code,display#en,Staatus
a1,A1,1
b1,B1,0
c1,C1,A
d1,D1,active"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "Staatus", propertyName: "status", propertyType: "string")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "concepts should have correct status values"
    codeSystem.concepts.size() == 4
    
    def a1 = codeSystem.concepts.find { it.code == "a1" }
    a1.versions[0].status == PublicationStatus.active
    
    def b1 = codeSystem.concepts.find { it.code == "b1" }
    b1.versions[0].status == PublicationStatus.retired
    
    def c1 = codeSystem.concepts.find { it.code == "c1" }
    c1.versions[0].status == PublicationStatus.active
    
    def d1 = codeSystem.concepts.find { it.code == "d1" }
    d1.versions[0].status == PublicationStatus.active
  }

  /**
   * BUSINESS: Test hierarchical concept with inferred parent
   * 
   * Validates that:
   * - Hierarchical concepts (using hierarchical-concept property) infer parent from prefix matching
   * - Parent association is created with type 'is-a'
   */
  def "should map hierarchical concept with inferred parent"() {
    given: "CSV file with hierarchical-concept property"
    String csvContent = """hierarchical-concept,display#en
1,Parent
11,Child of 1
111,Child of 11"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "hierarchical-concept", propertyName: "hierarchical-concept", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "hierarchical concepts should have inferred parent associations"
    codeSystem.concepts.size() == 3
    
    def child11 = codeSystem.concepts.find { it.code == "11" }
    child11 != null
    child11.versions[0].associations.size() == 1
    child11.versions[0].associations[0].targetCode == "1"
    
    def child111 = codeSystem.concepts.find { it.code == "111" }
    child111 != null
    child111.versions[0].associations.size() == 1
    child111.versions[0].associations[0].targetCode == "11"
  }

  /**
   * BUSINESS: Test CodeSystem and version metadata mapping
   * 
   * Validates that:
   * - CodeSystem metadata (id, uri, publisher, name, title, OID) is correctly mapped
   * - CodeSystemVersion metadata (version number, release date, algorithm, supported languages) is correctly mapped
   */
  def "should map CodeSystem and version metadata"() {
    given: "CSV file with minimal data and full CodeSystem metadata"
    String csvContent = """code,display#en
a1,A1"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(
        id: "test-cs",
        uri: "https://termx.org/fhir/CodeSystem/test-cs",
        publisher: "Test Publisher",
        name: "TestCodeSystem",
        title: ["en": "Test Code System"],
        oid: "1.2.3.4.5"
      ),
      version: new FileProcessingCodeSystemVersion(
        number: "1.0.0",
        language: "en",
        algorithm: "semver",
        releaseDate: java.time.LocalDate.of(2024, 1, 15),
        oid: "1.2.3.4.5.1"
      ),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "CodeSystem and version should have correct metadata"
    codeSystem.id == "test-cs"
    codeSystem.uri == "https://termx.org/fhir/CodeSystem/test-cs"
    codeSystem.publisher == "Test Publisher"
    codeSystem.name == "TestCodeSystem"
    codeSystem.title.get("en") == "Test Code System"
    codeSystem.identifiers.size() == 1
    codeSystem.identifiers[0].value == "urn:oid:1.2.3.4.5"
    
    codeSystem.versions.size() == 1
    def version = codeSystem.versions[0]
    version.version == "1.0.0"
    version.algorithm == "semver"
    version.releaseDate == java.time.LocalDate.of(2024, 1, 15)
    version.supportedLanguages.contains("en")
    version.identifiers.size() == 1
    version.identifiers[0].value == "urn:oid:1.2.3.4.5.1"
  }

  /**
   * BUSINESS: Test full end-to-end scenario with all property types
   * 
   * Validates that:
   * - All property types (string, decimal, bool, coding) are correctly mapped
   * - Designations, properties, and associations are all correctly mapped
   */
  def "should map full scenario with all property types"() {
    given: "CSV file with all property types"
    String csvContent = """code,display#en,definition#en,parent,status,comment,itemWeight,active,type#code,type#system
a1,A1,Definition text,1,active,Comment text,10.5,1,AdverseEvent,http://hl7.org/fhir/fhir-types"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "definition#en", propertyName: "definition", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "parent", propertyName: "parent", propertyType: "string"),
        new FileProcessingProperty(columnName: "status", propertyName: "status", propertyType: "string"),
        new FileProcessingProperty(columnName: "comment", propertyName: "comment", propertyType: "string"),
        new FileProcessingProperty(columnName: "itemWeight", propertyName: "itemWeight", propertyType: "decimal"),
        new FileProcessingProperty(columnName: "active", propertyName: "active", propertyType: bool),
        new FileProcessingProperty(columnName: "type#code", propertyName: "type", propertyType: "coding"),
        new FileProcessingProperty(columnName: "type#system", propertyName: "type", propertyType: "coding")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "concept should have all properties correctly mapped"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    def version = concept.versions[0]
    
    // Check designations
    version.designations.size() == 2
    version.designations.find { it.designationType == "display" }.name == "A1"
    version.designations.find { it.designationType == "definition" }.name == "Definition text"
    
    // Check associations
    version.associations.size() == 1
    version.associations[0].targetCode == "1"
    
    // Check property values (note: parent and status are special properties, not in propertyValues)
    def propertyValues = version.propertyValues
    assert propertyValues != null
    assert propertyValues.size() == 4  // comment, itemWeight, active, type
    
    def commentProp = propertyValues.find { it.entityProperty == "comment" }
    assert commentProp != null
    assert commentProp.value == "Comment text"
    
    def itemWeightProp = propertyValues.find { it.entityProperty == "itemWeight" }
    assert itemWeightProp != null
    assert itemWeightProp.value == 10.5
    
    def activeProp = propertyValues.find { it.entityProperty == "active" }
    assert activeProp != null
    assert activeProp.value == true
    
    def typeProp = propertyValues.find { it.entityProperty == "type" }
    assert typeProp != null
    assert typeProp.asCodingValue().code == "AdverseEvent"
  }

  /**
   * BUSINESS: Test date format detection and parsing (PDF requirement)
   * 
   * Validates that:
   * - Date values in different formats are correctly parsed
   * - Format is correctly applied from property configuration
   */
  def "should map date property with various formats"() {
    given: "CSV file with date columns in different formats"
    String csvContent = """code,display#en,date1,date2,date3,date4
a1,A1,2024-01-15,15.01.24,15.01.2024,01/15/2024"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "date1", propertyName: "date1", propertyType: "dateTime", propertyTypeFormat: "yyyy-MM-dd"),
        new FileProcessingProperty(columnName: "date2", propertyName: "date2", propertyType: "dateTime", propertyTypeFormat: "dd.MM.yy"),
        new FileProcessingProperty(columnName: "date3", propertyName: "date3", propertyType: "dateTime", propertyTypeFormat: "dd.MM.yyyy"),
        new FileProcessingProperty(columnName: "date4", propertyName: "date4", propertyType: "dateTime", propertyTypeFormat: "MM/dd/yyyy")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "date properties should be correctly parsed"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    def propertyValues = concept.versions[0].propertyValues
    propertyValues.size() == 4
    
    // All date values should be parsed (non-null Date objects)
    propertyValues.find { it.entityProperty == "date1" }.value != null
    propertyValues.find { it.entityProperty == "date2" }.value != null
    propertyValues.find { it.entityProperty == "date3" }.value != null
    propertyValues.find { it.entityProperty == "date4" }.value != null
  }

  /**
   * BUSINESS: Test delimiter handling for string properties (PDF requirement)
   * 
   * Validates that:
   * - Delimiter-separated values are split correctly
   * - Each part is trimmed
   * - Multiple property values are created
   */
  def "should map string property with delimiter"() {
    given: "CSV file with delimiter-separated string values"
    String csvContent = """code,display#en,synonyms
a1,A1,value1|value2|value3"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "synonyms", propertyName: "synonym", propertyType: "string", propertyDelimiter: "|")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "multiple property values should be created from delimited string"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    def propertyValues = concept.versions[0].propertyValues.findAll { it.entityProperty == "synonym" }
    propertyValues.size() == 3
    propertyValues*.value.containsAll(["value1", "value2", "value3"])
  }

  /**
   * BUSINESS: Test boolean detection and parsing (PDF requirement)
   * 
   * Validates that:
   * - Boolean values are correctly parsed (1/true = true, 0/false = false)
   * - Various boolean formats are handled
   */
  def "should map boolean property with various formats"() {
    given: "CSV file with boolean columns"
    String csvContent = """code,display#en,active1,active2,active3,active4
a1,A1,1,0,true,false"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "active1", propertyName: "active1", propertyType: bool),
        new FileProcessingProperty(columnName: "active2", propertyName: "active2", propertyType: bool),
        new FileProcessingProperty(columnName: "active3", propertyName: "active3", propertyType: bool),
        new FileProcessingProperty(columnName: "active4", propertyName: "active4", propertyType: bool)
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "boolean properties should be correctly parsed"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    def version = concept.versions[0]
    def propertyValues = version.propertyValues
    assert propertyValues != null
    assert propertyValues.size() == 4
    
    def active1 = propertyValues.find { it.entityProperty == "active1" }
    assert active1 != null
    assert active1.value == true
    
    def active2 = propertyValues.find { it.entityProperty == "active2" }
    assert active2 != null
    assert active2.value == false
    
    def active3 = propertyValues.find { it.entityProperty == "active3" }
    assert active3 != null
    assert active3.value == true
    
    def active4 = propertyValues.find { it.entityProperty == "active4" }
    assert active4 != null
    assert active4.value == false
  }

  /**
   * BUSINESS: Test identifier column auto-detection (PDF requirement)
   * 
   * Validates that:
   * - Columns named "id", "code", "identifier", or "kood" are automatically mapped to concept-code
   * - This is handled at the configuration level (processor level), but we test the end result
   */
  def "should map concept with identifier column variants"() {
    given: "CSV files with different identifier column names"
    // Note: Auto-detection happens at UI/configuration level, but we test that the mapping works
    // when these columns are correctly configured as concept-code
    String csvContent1 = """id,display#en
a1,A1"""
    
    String csvContent2 = """kood,display#en
a1,A1"""
    
    byte[] file1 = csvContent1.getBytes("UTF-8")
    byte[] file2 = csvContent2.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request1 = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "id", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
      ]
    )
    
    CodeSystemFileImportRequest request2 = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs2", name: "Test CS2"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "kood", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
      ]
    )
    
    when: "processing and mapping both imports"
    CodeSystemFileImportResult result1 = CodeSystemFileImportProcessor.process(request1, file1)
    CodeSystem codeSystem1 = CodeSystemFileImportMapper.toCodeSystem(
      request1.codeSystem, request1.version, result1, null, null
    )
    
    CodeSystemFileImportResult result2 = CodeSystemFileImportProcessor.process(request2, file2)
    CodeSystem codeSystem2 = CodeSystemFileImportMapper.toCodeSystem(
      request2.codeSystem, request2.version, result2, null, null
    )
    
    then: "both should correctly map concept code"
    codeSystem1.concepts.size() == 1
    codeSystem1.concepts[0].code == "a1"
    
    codeSystem2.concepts.size() == 1
    codeSystem2.concepts[0].code == "a1"
  }

  /**
   * BUSINESS: Test multiple date formats in same import
   * 
   * Validates that:
   * - Multiple date columns with different formats are correctly parsed
   * - Each date column uses its configured format
   */
  def "should map multiple date formats in same import"() {
    given: "CSV file with multiple date columns using different formats"
    String csvContent = """code,display#en,created,modified,released,expired
a1,A1,2024-01-15,15.01.2024,01/15/2024,15.01.24"""
    
    byte[] file = csvContent.getBytes("UTF-8")
    
    CodeSystemFileImportRequest request = new CodeSystemFileImportRequest(
      type: "csv",
      codeSystem: new FileProcessingCodeSystem(id: "test-cs", name: "Test CS"),
      version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
      properties: [
        new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en"),
        new FileProcessingProperty(columnName: "created", propertyName: "created", propertyType: "dateTime", propertyTypeFormat: "yyyy-MM-dd"),
        new FileProcessingProperty(columnName: "modified", propertyName: "modified", propertyType: "dateTime", propertyTypeFormat: "dd.MM.yyyy"),
        new FileProcessingProperty(columnName: "released", propertyName: "released", propertyType: "dateTime", propertyTypeFormat: "MM/dd/yyyy"),
        new FileProcessingProperty(columnName: "expired", propertyName: "expired", propertyType: "dateTime", propertyTypeFormat: "dd.MM.yy")
      ]
    )
    
    when: "processing and mapping the import"
    CodeSystemFileImportResult result = CodeSystemFileImportProcessor.process(request, file)
    CodeSystem codeSystem = CodeSystemFileImportMapper.toCodeSystem(
      request.codeSystem, request.version, result, null, null
    )
    
    then: "all date properties should be correctly parsed with their respective formats"
    codeSystem.concepts.size() == 1
    
    def concept = codeSystem.concepts[0]
    def propertyValues = concept.versions[0].propertyValues
    propertyValues.size() == 4
    
    // All date values should be parsed (non-null Date objects)
    propertyValues.find { it.entityProperty == "created" }.value != null
    propertyValues.find { it.entityProperty == "modified" }.value != null
    propertyValues.find { it.entityProperty == "released" }.value != null
    propertyValues.find { it.entityProperty == "expired" }.value != null
  }
}
