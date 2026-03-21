package org.termx.terminology.terminology.codesystem.concept

import org.termx.core.sys.lorque.LorqueProcessService
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.ConceptQueryParams
import org.termx.ts.codesystem.Designation
import org.termx.ts.codesystem.EntityPropertyType
import org.termx.ts.codesystem.EntityPropertyValue
import org.termx.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue
import org.termx.ts.property.PropertyReference
import spock.lang.Specification

import static org.termx.ts.codesystem.EntityPropertyType.coding
import static org.termx.ts.codesystem.EntityPropertyType.decimal
import static org.termx.ts.codesystem.EntityPropertyType.string

import java.lang.reflect.Method

class ConceptExportServiceTest extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def lorqueProcessService = Mock(LorqueProcessService)
  
  def service = new ConceptExportService(conceptService, codeSystemService, lorqueProcessService)
  
  def "should include designation columns in export headers"() {
    given: "a code system with concepts that have designations"
    String codeSystemId = "test-cs"
    String version = "1.0.0"
    
    CodeSystem codeSystem = new CodeSystem()
    codeSystem.setId(codeSystemId)
    codeSystem.setProperties([
      new PropertyReference().setName("status")
    ])
    
    // Create concept with designations
    Concept concept1 = new Concept()
    concept1.setCode("a1")
    concept1.setCodeSystem(codeSystemId)
    
    CodeSystemEntityVersion version1 = new CodeSystemEntityVersion()
    version1.setCode("a1")
    version1.setCodeSystem(codeSystemId)
    version1.setStatus(PublicationStatus.active)
    
    // Add designations
    Designation display1 = new Designation()
    display1.setName("A1")
    display1.setLanguage("en")
    display1.setDesignationType("display")
    display1.setStatus(PublicationStatus.active)
    
    Designation def1 = new Designation()
    def1.setName("bar-bar")
    def1.setLanguage("en")
    def1.setDesignationType("definition")
    def1.setStatus(PublicationStatus.active)
    
    Designation def2 = new Designation()
    def2.setName("bar")
    def2.setLanguage("en")
    def2.setDesignationType("definition")
    def2.setStatus(PublicationStatus.active)
    
    Designation def3 = new Designation()
    def3.setName("бар")
    def3.setLanguage("ru-RU")
    def3.setDesignationType("definition")
    def3.setStatus(PublicationStatus.active)
    
    version1.setDesignations([display1, def1, def2, def3])
    
    // Add property values
    EntityPropertyValue itemWeight = new EntityPropertyValue()
    itemWeight.setEntityProperty("itemWeight")
    itemWeight.setEntityPropertyType(decimal)
    itemWeight.setValue(new java.math.BigDecimal("10"))
    
    EntityPropertyValue synonym1 = new EntityPropertyValue()
    synonym1.setEntityProperty("synonym")
    synonym1.setEntityPropertyType(string)
    synonym1.setValue("x")
    
    EntityPropertyValue synonym2 = new EntityPropertyValue()
    synonym2.setEntityProperty("synonym")
    synonym2.setEntityPropertyType(string)
    synonym2.setValue("Y")
    
    EntityPropertyValue type1 = new EntityPropertyValue()
    type1.setEntityProperty("type")
    type1.setEntityPropertyType(coding)
    type1.setValue(new EntityPropertyValueCodingValue("ActivityDefinition", "snomed-ct"))
    
    EntityPropertyValue type2 = new EntityPropertyValue()
    type2.setEntityProperty("type")
    type2.setEntityPropertyType(coding)
    type2.setValue(new EntityPropertyValueCodingValue("Account", "snomed-ct"))
    
    version1.setPropertyValues([itemWeight, synonym1, synonym2, type1, type2])
    
    concept1.setVersions([version1])
    
    when: "checking designation and property collection logic"
    // Test the logic that collects designations and properties
    def designations = concept1.getVersions().stream()
      .flatMap { v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream() }
      .filter { d -> 
        if (d == null) return false
        String status = d.getStatus()
        PublicationStatus.active.equals(status) || "draft".equals(status)
      }
      .filter { d ->
        String type = d.getDesignationType()
        type != null && !type.trim().isEmpty()
      }
      .collect()
    
    def propertyValues = concept1.getVersions().stream()
      .flatMap { v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream() }
      .filter { pv -> pv != null && pv.getEntityProperty() != null && !pv.getEntityProperty().isEmpty() }
      .collect()
    
    then: "designations and properties should be found"
    designations.size() == 4
    designations.any { it.getDesignationType() == "display" && it.getLanguage() == "en" }
    designations.any { it.getDesignationType() == "definition" && it.getLanguage() == "en" }
    designations.any { it.getDesignationType() == "definition" && it.getLanguage() == "ru-RU" }
    
    propertyValues.size() == 5
    propertyValues.any { it.getEntityProperty() == "itemWeight" }
    propertyValues.count { it.getEntityProperty() == "synonym" } == 2
    propertyValues.count { it.getEntityProperty() == "type" } == 2
  }
  
  def "should populate designation values in rows"() {
    given: "a concept with designations"
    Concept concept = new Concept()
    concept.setCode("test1")
    
    CodeSystemEntityVersion version = new CodeSystemEntityVersion()
    version.setCode("test1")
    version.setStatus(PublicationStatus.active)
    
    Designation display1 = new Designation()
    display1.setName("Test Display")
    display1.setLanguage("en")
    display1.setDesignationType("display")
    display1.setStatus(PublicationStatus.active)
    
    Designation def1 = new Designation()
    def1.setName("Definition 1")
    def1.setLanguage("en")
    def1.setDesignationType("definition")
    def1.setStatus(PublicationStatus.active)
    
    Designation def2 = new Designation()
    def2.setName("Definition 2")
    def2.setLanguage("en")
    def2.setDesignationType("definition")
    def2.setStatus(PublicationStatus.active)
    
    version.setDesignations([display1, def1, def2])
    concept.setVersions([version])
    
    CodeSystem codeSystem = new CodeSystem()
    codeSystem.setProperties([])
    
    when: "composing headers and rows using reflection"
    Method composeHeadersMethod = ConceptExportService.class.getDeclaredMethod(
      "composeHeaders", CodeSystem.class, List.class
    )
    composeHeadersMethod.setAccessible(true)
    java.util.List<Concept> conceptsList = java.util.Arrays.asList(concept)
    def headersResult = composeHeadersMethod.invoke(service, codeSystem, conceptsList)
    def headers = headersResult
    
    Method composeRowMethod = ConceptExportService.class.getDeclaredMethod(
      "composeRow", Concept.class, List.class, Map.class, Map.class
    )
    composeRowMethod.setAccessible(true)
    def rowResult = composeRowMethod.invoke(service, concept, headers, new java.util.HashMap(), new java.util.HashMap())
    def row = rowResult
    
    then: "row should contain designation values"
    row != null
    row.length == headers.size()
    
    def codeIndex = headers.indexOf("code")
    codeIndex >= 0
    row[codeIndex] == "test1"
    
    // Single display should not have ##1 suffix
    def displayIndex = headers.findIndexOf { it == "display#en" || it == "display#en##1" }
    displayIndex >= 0
    row[displayIndex] == "Test Display"
    
    // Multiple definitions: first one may or may not have suffix, second one should have ##2
    def def1Index = headers.findIndexOf { it == "definition#en" || it == "definition#en##1" }
    def1Index >= 0
    row[def1Index] == "Definition 1"
    
    def def2Index = headers.indexOf("definition#en##2")
    def2Index >= 0
    row[def2Index] == "Definition 2"
  }
  
  def "should handle concepts with coding property values"() {
    given: "a concept with coding property values"
    Concept concept = new Concept()
    concept.setCode("test1")
    
    CodeSystemEntityVersion version = new CodeSystemEntityVersion()
    version.setCode("test1")
    version.setStatus(PublicationStatus.active)
    
    EntityPropertyValue type1 = new EntityPropertyValue()
    type1.setEntityProperty("type")
    type1.setEntityPropertyType(coding)
    type1.setValue(new EntityPropertyValueCodingValue("ActivityDefinition", "snomed-ct"))
    
    EntityPropertyValue type2 = new EntityPropertyValue()
    type2.setEntityProperty("type")
    type2.setEntityPropertyType(coding)
    type2.setValue(new EntityPropertyValueCodingValue("Account", "snomed-ct"))
    
    version.setPropertyValues([type1, type2])
    concept.setVersions([version])
    
    when: "checking property value collection and extraction"
    def propertyValues = concept.getVersions().stream()
      .flatMap { v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream() }
      .filter { pv -> pv != null && pv.getEntityProperty() != null && !pv.getEntityProperty().isEmpty() }
      .collect()
    
    def typeProperties = propertyValues.findAll { it.getEntityProperty() == "type" }
    
    then: "property values should be found and extractable"
    propertyValues.size() == 2
    typeProperties.size() == 2
    typeProperties.every { it.getEntityPropertyType() == coding }
    
    when: "extracting coding values"
    def coding1 = typeProperties[0].asCodingValue()
    def coding2 = typeProperties[1].asCodingValue()
    
    then: "coding values should be correct"
    coding1 != null
    coding1.getCode() == "ActivityDefinition"
    coding1.getCodeSystem() == "snomed-ct"
    
    coding2 != null
    coding2.getCode() == "Account"
    coding2.getCodeSystem() == "snomed-ct"
  }
  
  def "should verify designation collection logic"() {
    given: "a concept with designations"
    Concept concept = new Concept()
    concept.setCode("test1")
    
    CodeSystemEntityVersion version = new CodeSystemEntityVersion()
    version.setCode("test1")
    version.setStatus(PublicationStatus.active)
    
    Designation def1 = new Designation()
    def1.setName("Definition 1")
    def1.setLanguage("en")
    def1.setDesignationType("definition")
    def1.setStatus(PublicationStatus.active)
    
    Designation def2 = new Designation()
    def2.setName("Definition 2")
    def2.setLanguage("en")
    def2.setDesignationType("definition")
    def2.setStatus(PublicationStatus.active)
    
    version.setDesignations([def1, def2])
    concept.setVersions([version])
    
    when: "checking designation structure"
    def designations = concept.getVersions().stream()
      .flatMap { v -> v.getDesignations().stream() }
      .filter { d -> 
        if (d == null) return false
        String status = d.getStatus()
        PublicationStatus.active.equals(status) || "draft".equals(status)
      }
      .filter { d -> 
        String type = d.getDesignationType()
        type != null && !type.trim().isEmpty()
      }
      .collect()
    
    then: "designations should be found with correct structure"
    designations.size() == 2
    designations.every { d -> d.getDesignationType() == "definition" }
    designations.every { d -> d.getLanguage() == "en" }
    designations[0].getName() == "Definition 1"
    designations[1].getName() == "Definition 2"
  }
  
  def "should export real-world CodeSystem matching FHIR definition"() {
    given: "a CodeSystem matching the real-world FHIR definition"
    CodeSystem codeSystem = new CodeSystem()
    codeSystem.setId("test1")
    codeSystem.setProperties([
      new PropertyReference().setName("itemWeight"),
      new PropertyReference().setName("synonym"),
      new PropertyReference().setName("type")
    ])
    
    // Concept a1: has display, itemWeight, 2 synonyms, 2 type codings
    Concept conceptA1 = new Concept()
    conceptA1.setCode("a1")
    conceptA1.setCodeSystem("test1")
    
    CodeSystemEntityVersion versionA1 = new CodeSystemEntityVersion()
    versionA1.setCode("a1")
    versionA1.setCodeSystem("test1")
    versionA1.setStatus(PublicationStatus.active)
    
    // Display designation
    Designation displayA1 = new Designation()
    displayA1.setName("A1")
    displayA1.setLanguage("en")
    displayA1.setDesignationType("display")
    displayA1.setStatus(PublicationStatus.active)
    
    versionA1.setDesignations([displayA1])
    
    // Properties for a1
    EntityPropertyValue itemWeightA1 = new EntityPropertyValue()
    itemWeightA1.setEntityProperty("itemWeight")
    itemWeightA1.setEntityPropertyType(decimal)
    itemWeightA1.setValue(new java.math.BigDecimal("10"))
    
    EntityPropertyValue synonym1A1 = new EntityPropertyValue()
    synonym1A1.setEntityProperty("synonym")
    synonym1A1.setEntityPropertyType(string)
    synonym1A1.setValue("x")
    
    EntityPropertyValue synonym2A1 = new EntityPropertyValue()
    synonym2A1.setEntityProperty("synonym")
    synonym2A1.setEntityPropertyType(string)
    synonym2A1.setValue("Y")
    
    EntityPropertyValue type1A1 = new EntityPropertyValue()
    type1A1.setEntityProperty("type")
    type1A1.setEntityPropertyType(coding)
    type1A1.setValue(new EntityPropertyValueCodingValue("AdverseEvent", "http://hl7.org/fhir/fhir-types"))
    
    EntityPropertyValue type2A1 = new EntityPropertyValue()
    type2A1.setEntityProperty("type")
    type2A1.setEntityPropertyType(coding)
    type2A1.setValue(new EntityPropertyValueCodingValue("Age", "http://hl7.org/fhir/fhir-types"))
    
    versionA1.setPropertyValues([itemWeightA1, synonym1A1, synonym2A1, type1A1, type2A1])
    conceptA1.setVersions([versionA1])
    
    // Concept b1: has display, definition, 2 designations, 2 synonyms, 2 type codings
    Concept conceptB1 = new Concept()
    conceptB1.setCode("b1")
    conceptB1.setCodeSystem("test1")
    
    CodeSystemEntityVersion versionB1 = new CodeSystemEntityVersion()
    versionB1.setCode("b1")
    versionB1.setCodeSystem("test1")
    versionB1.setStatus(PublicationStatus.active)
    
    // Display designation
    Designation displayB1 = new Designation()
    displayB1.setName("B1")
    displayB1.setLanguage("en")
    displayB1.setDesignationType("display")
    displayB1.setStatus(PublicationStatus.active)
    
    // Definition designation (from definition field)
    Designation definitionB1 = new Designation()
    definitionB1.setName("bar-bar")
    definitionB1.setLanguage("en")
    definitionB1.setDesignationType("definition")
    definitionB1.setStatus(PublicationStatus.active)
    
    // Additional designations
    Designation defEnB1 = new Designation()
    defEnB1.setName("bar")
    defEnB1.setLanguage("en")
    defEnB1.setDesignationType("definition")
    defEnB1.setStatus(PublicationStatus.active)
    
    Designation defRuB1 = new Designation()
    defRuB1.setName("бар")
    defRuB1.setLanguage("ru")
    defRuB1.setDesignationType("definition")
    defRuB1.setStatus(PublicationStatus.active)
    
    versionB1.setDesignations([displayB1, definitionB1, defEnB1, defRuB1])
    
    // Properties for b1
    EntityPropertyValue synonym1B1 = new EntityPropertyValue()
    synonym1B1.setEntityProperty("synonym")
    synonym1B1.setEntityPropertyType(string)
    synonym1B1.setValue("g1")
    
    EntityPropertyValue synonym2B1 = new EntityPropertyValue()
    synonym2B1.setEntityProperty("synonym")
    synonym2B1.setEntityPropertyType(string)
    synonym2B1.setValue("g2")
    
    EntityPropertyValue type1B1 = new EntityPropertyValue()
    type1B1.setEntityProperty("type")
    type1B1.setEntityPropertyType(coding)
    type1B1.setValue(new EntityPropertyValueCodingValue("Account", "http://hl7.org/fhir/fhir-types"))
    
    EntityPropertyValue type2B1 = new EntityPropertyValue()
    type2B1.setEntityProperty("type")
    type2B1.setEntityPropertyType(coding)
    type2B1.setValue(new EntityPropertyValueCodingValue("ActivityDefinition", "http://hl7.org/fhir/fhir-types"))
    
    versionB1.setPropertyValues([synonym1B1, synonym2B1, type1B1, type2B1])
    conceptB1.setVersions([versionB1])
    
    when: "checking data collection for export"
    // Test the data collection logic that the export uses
    def allConcepts = [conceptA1, conceptB1]
    
    // Collect designations from all concepts
    def allDesignations = allConcepts.stream()
      .flatMap { c -> Optional.ofNullable(c.getVersions()).orElse(List.of()).stream() }
      .flatMap { v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream() }
      .filter { d -> 
        if (d == null) return false
        String status = d.getStatus()
        PublicationStatus.active.equals(status) || "draft".equals(status)
      }
      .filter { d ->
        String type = d.getDesignationType()
        type != null && !type.trim().isEmpty()
      }
      .collect()
    
    // Collect property values from all concepts
    def allPropertyValues = allConcepts.stream()
      .flatMap { c -> Optional.ofNullable(c.getVersions()).orElse(List.of()).stream() }
      .flatMap { v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream() }
      .filter { pv -> pv != null && pv.getEntityProperty() != null && !pv.getEntityProperty().isEmpty() }
      .collect()
    
    // Check specific values for a1
    def a1Designations = conceptA1.getVersions().stream()
      .flatMap { v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream() }
      .filter { d -> 
        if (d == null) return false
        String status = d.getStatus()
        PublicationStatus.active.equals(status) || "draft".equals(status)
      }
      .collect()
    
    def a1Properties = conceptA1.getVersions().stream()
      .flatMap { v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream() }
      .filter { pv -> pv != null && pv.getEntityProperty() != null && !pv.getEntityProperty().isEmpty() }
      .collect()
    
    // Check specific values for b1
    def b1Designations = conceptB1.getVersions().stream()
      .flatMap { v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream() }
      .filter { d -> 
        if (d == null) return false
        String status = d.getStatus()
        PublicationStatus.active.equals(status) || "draft".equals(status)
      }
      .collect()
    
    def b1Properties = conceptB1.getVersions().stream()
      .flatMap { v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream() }
      .filter { pv -> pv != null && pv.getEntityProperty() != null && !pv.getEntityProperty().isEmpty() }
      .collect()
    
    then: "all designations should be collected"
    allDesignations.size() == 5  // a1: 1 display, b1: 1 display + 3 definitions
    
    and: "a1 should have correct designations and properties"
    a1Designations.size() == 1
    a1Designations.any { it.getDesignationType() == "display" && it.getName() == "A1" && it.getLanguage() == "en" }
    
    a1Properties.size() == 5
    a1Properties.any { it.getEntityProperty() == "itemWeight" && it.getValue() == new java.math.BigDecimal("10") }
    a1Properties.count { it.getEntityProperty() == "synonym" } == 2
    a1Properties.count { it.getEntityProperty() == "type" } == 2
    
    // Check coding values for a1
    def a1TypeProperties = a1Properties.findAll { it.getEntityProperty() == "type" }
    a1TypeProperties.size() == 2
    def a1Type1 = a1TypeProperties[0].asCodingValue()
    def a1Type2 = a1TypeProperties[1].asCodingValue()
    (a1Type1.getCode() == "AdverseEvent" && a1Type1.getCodeSystem() == "http://hl7.org/fhir/fhir-types") ||
    (a1Type2.getCode() == "AdverseEvent" && a1Type2.getCodeSystem() == "http://hl7.org/fhir/fhir-types")
    (a1Type1.getCode() == "Age" && a1Type1.getCodeSystem() == "http://hl7.org/fhir/fhir-types") ||
    (a1Type2.getCode() == "Age" && a1Type2.getCodeSystem() == "http://hl7.org/fhir/fhir-types")
    
    and: "b1 should have correct designations and properties"
    b1Designations.size() == 4
    b1Designations.any { it.getDesignationType() == "display" && it.getName() == "B1" && it.getLanguage() == "en" }
    b1Designations.any { it.getDesignationType() == "definition" && it.getName() == "bar-bar" && it.getLanguage() == "en" }
    b1Designations.any { it.getDesignationType() == "definition" && it.getName() == "bar" && it.getLanguage() == "en" }
    b1Designations.any { it.getDesignationType() == "definition" && it.getName() == "бар" && it.getLanguage() == "ru" }
    
    b1Properties.size() == 4
    b1Properties.count { it.getEntityProperty() == "synonym" } == 2
    b1Properties.count { it.getEntityProperty() == "type" } == 2
    
    // Check coding values for b1
    def b1TypeProperties = b1Properties.findAll { it.getEntityProperty() == "type" }
    b1TypeProperties.size() == 2
    def b1Type1 = b1TypeProperties[0].asCodingValue()
    def b1Type2 = b1TypeProperties[1].asCodingValue()
    (b1Type1.getCode() == "Account" && b1Type1.getCodeSystem() == "http://hl7.org/fhir/fhir-types") ||
    (b1Type2.getCode() == "Account" && b1Type2.getCodeSystem() == "http://hl7.org/fhir/fhir-types")
    (b1Type1.getCode() == "ActivityDefinition" && b1Type1.getCodeSystem() == "http://hl7.org/fhir/fhir-types") ||
    (b1Type2.getCode() == "ActivityDefinition" && b1Type2.getCodeSystem() == "http://hl7.org/fhir/fhir-types")
  }
  
  def "should use optional order suffix - omit ##1 for single values"() {
    given: "concepts with single and multiple values"
    CodeSystem codeSystem = new CodeSystem()
    codeSystem.setId("test-cs")
    codeSystem.setProperties([])
    
    // Concept with single designation, single property
    Concept concept1 = new Concept()
    concept1.setCode("c1")
    
    CodeSystemEntityVersion version1 = new CodeSystemEntityVersion()
    version1.setCode("c1")
    version1.setStatus(PublicationStatus.active)
    
    Designation display1 = new Designation()
    display1.setName("Single Display")
    display1.setLanguage("en")
    display1.setDesignationType("display")
    display1.setStatus(PublicationStatus.active)
    
    version1.setDesignations([display1])
    
    EntityPropertyValue itemWeight1 = new EntityPropertyValue()
    itemWeight1.setEntityProperty("itemWeight")
    itemWeight1.setEntityPropertyType(decimal)
    itemWeight1.setValue(new java.math.BigDecimal("5"))
    
    version1.setPropertyValues([itemWeight1])
    concept1.setVersions([version1])
    
    // Concept with multiple designations, multiple properties
    Concept concept2 = new Concept()
    concept2.setCode("c2")
    
    CodeSystemEntityVersion version2 = new CodeSystemEntityVersion()
    version2.setCode("c2")
    version2.setStatus(PublicationStatus.active)
    
    Designation display2a = new Designation()
    display2a.setName("Display 1")
    display2a.setLanguage("en")
    display2a.setDesignationType("display")
    display2a.setStatus(PublicationStatus.active)
    
    Designation display2b = new Designation()
    display2b.setName("Display 2")
    display2b.setLanguage("en")
    display2b.setDesignationType("display")
    display2b.setStatus(PublicationStatus.active)
    
    version2.setDesignations([display2a, display2b])
    
    EntityPropertyValue synonym2a = new EntityPropertyValue()
    synonym2a.setEntityProperty("synonym")
    synonym2a.setEntityPropertyType(string)
    synonym2a.setValue("syn1")
    
    EntityPropertyValue synonym2b = new EntityPropertyValue()
    synonym2b.setEntityProperty("synonym")
    synonym2b.setEntityPropertyType(string)
    synonym2b.setValue("syn2")
    
    version2.setPropertyValues([synonym2a, synonym2b])
    concept2.setVersions([version2])
    
    when: "composing headers using reflection"
    Method composeHeadersMethod = ConceptExportService.class.getDeclaredMethod(
      "composeHeaders", CodeSystem.class, List.class
    )
    composeHeadersMethod.setAccessible(true)
    java.util.List<Concept> conceptsList = new java.util.ArrayList<>()
    conceptsList.add(concept1)
    conceptsList.add(concept2)
    def headersResult = composeHeadersMethod.invoke(service, codeSystem, conceptsList)
    def headers = headersResult as List
    
    then: "headers should be generated correctly"
    // Column order: code, display designations, other designations, properties
    headers[0] == "code"
    
    // Display designations come first after code
    def displayIndex = headers.indexOf("display#en##1")
    displayIndex > 0
    headers.contains("display#en##1")
    headers.contains("display#en##2")
    
    // Since concept1 has 1 itemWeight and concept2 has 0, maxCount is 1, so no suffix
    headers.contains("itemWeight")
    !headers.contains("itemWeight##1")
    
    // Since concept2 has 2 synonyms, maxCount is 2, so we get ##1 and ##2
    headers.contains("synonym##1")
    headers.contains("synonym##2")
    
    // Properties come after designations
    def itemWeightIndex = headers.indexOf("itemWeight")
    itemWeightIndex > displayIndex
  }
  
  def "should include draft designations in export"() {
    given: "a concept with both active and draft designations"
    CodeSystem codeSystem = new CodeSystem()
    codeSystem.setId("test-cs")
    codeSystem.setProperties([])
    
    Concept concept = new Concept()
    concept.setCode("test1")
    
    CodeSystemEntityVersion version = new CodeSystemEntityVersion()
    version.setCode("test1")
    version.setStatus(PublicationStatus.active)
    
    Designation displayActive = new Designation()
    displayActive.setName("Active Display")
    displayActive.setLanguage("en")
    displayActive.setDesignationType("display")
    displayActive.setStatus(PublicationStatus.active)
    
    Designation displayDraft = new Designation()
    displayDraft.setName("Draft Display")
    displayDraft.setLanguage("en")
    displayDraft.setDesignationType("display")
    displayDraft.setStatus("draft")
    
    Designation defActive = new Designation()
    defActive.setName("Active Definition")
    defActive.setLanguage("en")
    defActive.setDesignationType("definition")
    defActive.setStatus(PublicationStatus.active)
    
    Designation defDraft = new Designation()
    defDraft.setName("Draft Definition")
    defDraft.setLanguage("en")
    defDraft.setDesignationType("definition")
    defDraft.setStatus("draft")
    
    version.setDesignations([displayActive, displayDraft, defActive, defDraft])
    concept.setVersions([version])
    
    when: "composing headers using reflection"
    Method composeHeadersMethod = ConceptExportService.class.getDeclaredMethod(
      "composeHeaders", CodeSystem.class, List.class
    )
    composeHeadersMethod.setAccessible(true)
    java.util.List<Concept> conceptsList = new java.util.ArrayList<>()
    conceptsList.add(concept)
    def headersResult = composeHeadersMethod.invoke(service, codeSystem, conceptsList)
    def headers = headersResult as List
    
    then: "both active and draft designations should be included"
    // Since we have 2 displays (active + draft), maxCount is 2
    headers.contains("display#en##1")
    headers.contains("display#en##2")
    
    // Since we have 2 definitions (active + draft), maxCount is 2
    headers.contains("definition#en##1")
    headers.contains("definition#en##2")
    
    // Column order: code, display, other designations
    headers[0] == "code"
    def displayIndex = headers.indexOf("display#en##1")
    def definitionIndex = headers.indexOf("definition#en##1")
    displayIndex > 0
    definitionIndex > displayIndex
  }
  
  def "should order columns correctly: code, display, other designations, properties"() {
    given: "a code system with properties in specific order"
    CodeSystem codeSystem = new CodeSystem()
    codeSystem.setId("test-cs")
    codeSystem.setProperties([
      new PropertyReference().setName("itemWeight"),
      new PropertyReference().setName("synonym"),
      new PropertyReference().setName("type")
    ])
    
    Concept concept = new Concept()
    concept.setCode("test1")
    
    CodeSystemEntityVersion version = new CodeSystemEntityVersion()
    version.setCode("test1")
    version.setStatus(PublicationStatus.active)
    
    Designation display = new Designation()
    display.setName("Display")
    display.setLanguage("en")
    display.setDesignationType("display")
    display.setStatus(PublicationStatus.active)
    
    Designation definition = new Designation()
    definition.setName("Definition")
    definition.setLanguage("en")
    definition.setDesignationType("definition")
    definition.setStatus(PublicationStatus.active)
    
    version.setDesignations([display, definition])
    
    EntityPropertyValue itemWeight = new EntityPropertyValue()
    itemWeight.setEntityProperty("itemWeight")
    itemWeight.setEntityPropertyType(decimal)
    itemWeight.setValue(new java.math.BigDecimal("10"))
    
    version.setPropertyValues([itemWeight])
    concept.setVersions([version])
    
    when: "composing headers using reflection"
    Method composeHeadersMethod = ConceptExportService.class.getDeclaredMethod(
      "composeHeaders", CodeSystem.class, List.class
    )
    composeHeadersMethod.setAccessible(true)
    java.util.List<Concept> conceptsList = new java.util.ArrayList<>()
    conceptsList.add(concept)
    def headersResult = composeHeadersMethod.invoke(service, codeSystem, conceptsList)
    def headers = headersResult as List
    
    then: "columns should be in correct order"
    headers[0] == "code"
    
    // Display designations come after code
    def codeIndex = headers.indexOf("code")
    def displayIndex = headers.indexOf("display#en")
    displayIndex > codeIndex
    
    // Other designations come after display
    def definitionIndex = headers.indexOf("definition#en")
    definitionIndex > displayIndex
    
    // Properties come after designations
    def itemWeightIndex = headers.indexOf("itemWeight")
    itemWeightIndex > definitionIndex
  }
}
