package com.kodality.zmei.fhir.resource.terminology;

import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude;
import com.kodality.zmei.fhir.util.JsonAssert;
import com.kodality.zmei.fhir.util.TestUtils;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ValueSetSerializationTest {
  private static final String EXAMPLE_EXTENSIONAL = "/examples/valueset/example-extensional.json";
  private static final String EXAMPLE_INTENSIONAL = "/examples/valueset/example-intensional.json";
  private static final String EXAMPLE_HIERARCHICAL = "/examples/valueset/example-hierarchical.json";

  @Test
  public void testValueSetDeserialization() throws IOException {
    String json = TestUtils.readResource(EXAMPLE_EXTENSIONAL);
    Resource resource = FhirMapper.getObjectMapper().readValue(json, Resource.class);

    assertTrue("Should deserialize as ValueSet", resource instanceof ValueSet);
    ValueSet cs = (ValueSet) resource;

    assertEquals("LOINC Codes for Cholesterol in Serum/Plasma", cs.getName());
    assertEquals("example-extensional", cs.getId());
    assertEquals("draft", cs.getStatus());

    ValueSetComposeInclude include = cs.getCompose().getInclude().get(0);
    assertEquals(4, include.getConcept().size());
    assertEquals("14647-2", include.getConcept().get(0).getCode());
    assertEquals("2093-3", include.getConcept().get(1).getCode());
    assertEquals("35200-5", include.getConcept().get(2).getCode());
    assertEquals("9342-7", include.getConcept().get(3).getCode());
  }

  @Test
  public void testCanSerializeIntensionalValueSet() throws IOException {
    String refJson = TestUtils.readResource(EXAMPLE_INTENSIONAL);
    Resource resource = FhirMapper.getObjectMapper().readValue(refJson, Resource.class);
    String json = FhirMapper.toJson(resource);

    // XXX dateTime type serialization is kind of broken
    json = TestUtils.fixDate(json, "2015-06-22");


    JsonAssert.assertJsonEquals(refJson, json);
  }

  @Test
  public void testCanSerializeHierarchicalValueSet() throws IOException {
    String refJson = TestUtils.readResource(EXAMPLE_HIERARCHICAL);
    Resource resource = FhirMapper.getObjectMapper().readValue(refJson, Resource.class);
    String json = FhirMapper.toJson(resource);

    // XXX dateTime type serialization is kind of broken
    json = TestUtils.fixDate(json, "2018-07-20");

    JsonAssert.assertJsonEquals(refJson, json);
  }

  @Test
  public void testCanSerializeExtensionalValueSet() throws IOException {
    String refJson = TestUtils.readResource(EXAMPLE_EXTENSIONAL);
    Resource resource = FhirMapper.getObjectMapper().readValue(refJson, Resource.class);
    String json = FhirMapper.toJson(resource);

    // XXX dateTime type serialization is kind of broken
    json = TestUtils.fixDate(json, "2015-06-22");
    json = TestUtils.fixDate(json, "2012-06-13");

    JsonAssert.assertJsonEquals(refJson, json);
  }

}
