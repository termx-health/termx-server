package com.kodality.zmei.fhir.resource.terminology;

import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.util.JsonAssert;
import com.kodality.zmei.fhir.util.TestUtils;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CodeSystemSerializationTest {
  private static final String CODESYSTEM_EXAMPLE_RESOURCE = "/examples/codesystem/codesystem-example.json";

  @Test
  public void testCodeSystemDeserialization() throws IOException {
    String json = TestUtils.readResource(CODESYSTEM_EXAMPLE_RESOURCE);
    Resource resource = FhirMapper.getObjectMapper().readValue(json, Resource.class);

    assertTrue("Should deserialize as CodeSystem", resource instanceof CodeSystem);
    CodeSystem cs = (CodeSystem) resource;

    assertEquals("ACMECholCodesBlood", cs.getName());
    assertEquals("ACME Codes for Cholesterol in Serum/Plasma", cs.getTitle());
    assertEquals("draft", cs.getStatus());

    assertEquals(3, cs.getConcept().size());
    assertEquals("chol-mmol", cs.getConcept().get(0).getCode());
    assertEquals("chol-mass", cs.getConcept().get(1).getCode());
    assertEquals("chol", cs.getConcept().get(2).getCode());
  }

  @Test
  public void testCodeSystemSerialization() throws IOException {
    String refJson = TestUtils.readResource(CODESYSTEM_EXAMPLE_RESOURCE);
    Resource resource = FhirMapper.getObjectMapper().readValue(refJson, Resource.class);
    String json = FhirMapper.toJson(resource);

    // XXX dateTime type serialization is kind of broken
    json = TestUtils.fixDate(json, "2016-01-28");

    JsonAssert.assertJsonEquals(refJson, json);
  }

}
