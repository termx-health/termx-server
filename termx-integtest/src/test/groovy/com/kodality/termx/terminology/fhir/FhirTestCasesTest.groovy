package com.kodality.termx.terminology.fhir

import com.kodality.commons.util.JsonUtil
import com.kodality.termx.TermxIntegTest
import com.kodality.termx.auth.SessionInfo
import com.kodality.termx.auth.SessionStore
import com.kodality.termx.fhir.codesystem.CodeSystemFhirImportService
import com.kodality.termx.fhir.valueset.ValueSetFhirImportService
import com.kodality.termx.fhir.valueset.operations.ValueSetExpandOperation
import com.kodality.termx.fhir.valueset.operations.ValueSetValidateCodeOperation
import com.kodality.termx.terminology.codesystem.CodeSystemService
import com.kodality.termx.terminology.valueset.ValueSetService
import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.Resource
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.terminology.ValueSet
import groovy.util.logging.Slf4j
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.commons.lang3.tuple.Pair
import spock.lang.Shared
import spock.lang.Unroll

import java.util.stream.Collectors

@Slf4j
@MicronautTest(transactional = true)
class FhirTestCasesTest extends TermxIntegTest {
  @Inject
  CodeSystemFhirImportService csImportService
  @Inject
  CodeSystemService csService

  @Inject
  ValueSetFhirImportService vsImportService
  @Inject
  ValueSetService vsService
  @Inject
  ValueSetExpandOperation vsExpand
  @Inject
  ValueSetValidateCodeOperation vsValidateCode

  @Shared
  def tests = JsonUtil.fromJson(new String(getClass().getClassLoader().getResourceAsStream("fhir/test-cases.json").readAllBytes()), FhirTestCase.class)
      .suites.stream().flatMap(s -> s.tests.stream().map(t -> Pair.of(s, t))).toList()


  void setup() {
    def sessionInfo = new SessionInfo();
    sessionInfo.privileges = ['admin']
    SessionStore.setLocal(sessionInfo)
  }

  @Unroll
  def "SUIT #test.left.name TEST #test.right.name"() {
    when:
    runSetup(test.left.setup)
    then:
    runTest(test.right)
    where:
    test << tests
  }

  def runSetup(List<String> setup) {
    setup.forEach(s -> deleteResource(s))
    setup.forEach(s -> importResource(s))
  }

  def runTest(FhirTestCase.FhirTest test) {
    def request = toFhir(test.request, Parameters.class)
    if (test.operation == "expand") {
      def response = toFhir(test.response, ValueSet.class)
      checkExpandResult(vsExpand.run(request), response)
    } else if (test.operation == "validate-code") {
      def response = toFhir(test.response, Parameters.class)
      checkValidateCodeResult(vsValidateCode.run(request), response)
    } else {
      false
    }
  }

  def importResource(String path) {
    def resource = toFhir(path, Resource.class)
    if (resource.resourceType == "CodeSystem") {
      csImportService.importCodeSystem(FhirMapper.toJson(resource), resource.id)
    }
    if (resource.resourceType == "ValueSet") {
      vsImportService.importValueSet(FhirMapper.toJson(resource), resource.id)
    }
  }

  def deleteResource(String path) {
    def resource = toFhir(path, Resource.class)
    if (resource.resourceType == "CodeSystem") {
      csService.cancel(resource.id)
    }
    if (resource.resourceType == "ValueSet") {
      vsService.cancel(resource.id)
    }
  }

  Map<String, List<String>> toMap(Parameters fhirParams) {
    def grouped = fhirParams.parameter.stream().collect(Collectors.groupingBy(p -> p.name))
    def map = new HashMap()
    grouped.keySet().forEach(k -> map.put(k, getFhirValue(grouped.get(k))))
    return map
  }

  List<String> getFhirValue(List<Parameters.ParametersParameter> parameters) {
    return parameters.stream().map(p -> {
      def stringValues = new ArrayList()
      stringValues.addAll(p.valueCode, p.valueString, p.valueUrl, p.valueUri, p.valueCanonical, p.valueUuid)
      def value = stringValues.stream().filter(Objects::nonNull).findFirst()
      if (value.isPresent()) {
        return value.get()
      }
      if (p.valueBoolean) {
        return String.valueOf(p.valueBoolean)
      }
      if (p.valueInteger) {
        return String.valueOf(p.valueInteger)
      }
      return null
    }).filter(Objects::nonNull).collect(Collectors.toList())
  }

  def checkExpandResult(ValueSet actualValueSet, ValueSet expectedValueSet) {
    actualValueSet.url == expectedValueSet.url
    actualValueSet.version == expectedValueSet.version
    actualValueSet.status == expectedValueSet.status
    actualValueSet.name == expectedValueSet.name
    actualValueSet.title == expectedValueSet.title
    actualValueSet.experimental == expectedValueSet.experimental
    actualValueSet.publisher == expectedValueSet.publisher
    actualValueSet.description == expectedValueSet.description
    actualValueSet.immutable == expectedValueSet.immutable
    actualValueSet.purpose == expectedValueSet.purpose
    actualValueSet.copyright == expectedValueSet.copyright
    actualValueSet.copyrightLabel == expectedValueSet.copyrightLabel
    if (actualValueSet.expansion != null && expectedValueSet.expansion != null) {
      actualValueSet.expansion.total == expectedValueSet.expansion.total
      checkContains(actualValueSet.expansion.contains, expectedValueSet.expansion.contains)
    } else {
      actualValueSet.expansion == null
      expectedValueSet.expansion == null
    }
  }


  def checkContains(List<ValueSet.ValueSetExpansionContains> actualContains, List<ValueSet.ValueSetExpansionContains> expectedContains) {
    actualContains.stream().noneMatch { ac -> {
      boolean check = checkContainsSingle(ac, expectedContains.stream().filter { ec -> (ec.code == ac.code) }.findFirst().orElse(null))
      return !check
    }}
  }


  boolean checkContainsSingle(ValueSet.ValueSetExpansionContains actualContains, ValueSet.ValueSetExpansionContains expectedContains) {
    println '-------------------------'
    println '--------ACTUAL-----------'
    println actualContains.display
    println actualContains.designation
    if (actualContains.designation != null) {
      println('designation -> ')
      actualContains.designation.forEach {d -> {
        println d.value
        println d.language
      }}
    }
    println '--------EXPECTED---------'
    println expectedContains.display
    println expectedContains.designation
    if (expectedContains.designation != null) {
      println('designation -> ')
      expectedContains.designation.forEach {d -> {
        println d.value
        println d.language
      }}
    }
    return actualContains != null &&
        expectedContains != null &&
        actualContains.code == expectedContains.code &&
        actualContains.display == expectedContains.display &&
        actualContains.system == expectedContains.system &&
        actualContains.inactive == expectedContains.inactive &&
        (!isEmpty(actualContains.designation) && !isEmpty(expectedContains.designation) && checkDesignations(actualContains.designation, expectedContains.designation)
            || isEmpty(actualContains.designation) && isEmpty(expectedContains.designation)) &&
        (!isEmpty(actualContains.contains) && !isEmpty(expectedContains.contains) && checkContains(actualContains.contains, expectedContains.contains)
            || isEmpty(actualContains.contains) && isEmpty(expectedContains.contains))
  }

  def checkDesignations(List<ValueSet.ValueSetComposeIncludeConceptDesignation> actualDesignations, List<ValueSet.ValueSetComposeIncludeConceptDesignation> expectedDesignations) {
    actualDesignations.stream().noneMatch { ad -> {
      boolean check = checkDesignationsSingle(ad, expectedDesignations.stream().filter { ed -> (ad.value == ed.value && (ed.language == null || ed.language == ad.language)) }.findFirst().orElse(null))
      return !check
    }}
  }

  boolean checkDesignationsSingle(ValueSet.ValueSetComposeIncludeConceptDesignation actualDesignation, ValueSet.ValueSetComposeIncludeConceptDesignation expectedDesignation) {
    return actualDesignation != null &&
        expectedDesignation != null &&
        actualDesignation.value == expectedDesignation.value &&
        (expectedDesignation.language == null || actualDesignation.language == expectedDesignation.language) &&
        (expectedDesignation.use == null || actualDesignation.use.code == expectedDesignation.use.code)
  }

  <T> boolean isEmpty(List<T> contains) {
    return contains == null || contains == []
  }

  def checkValidateCodeResult(Parameters actualResult, Parameters expectedResult) {
    actualResult.findParameter("result").isPresent()
    expectedResult.findParameter("result").isPresent()
  }

  def <T extends Resource> T toFhir(String path, Class<T> clazz) {
    def json = new String(getClass().getClassLoader().getResourceAsStream("fhir/" + path).readAllBytes())
    json = json.replace("\uFEFF", "")
    json = json.replace("\$" + "instant" + "\$", "")
    return FhirMapper.fromJson(json, clazz)
  }
}
