package com.kodality.zmei.fhir.resource.infrastructure;

import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.individual.Patient;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.util.JsonAssert;
import com.kodality.zmei.fhir.util.TestUtils;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParametersSerializationTest {
  private static final String PARAMETERS_EXAMPLE_RESOURCE = "/examples/parameters/parameters-example.json";

  @Test
  public void testCodeSystemDeserialization() throws IOException {
    String json = TestUtils.readResource(PARAMETERS_EXAMPLE_RESOURCE);
    Resource resource = FhirMapper.getObjectMapper().readValue(json, Resource.class);

    assertTrue("Should deserialize as Parameters", resource instanceof Parameters);
    Parameters ps = (Parameters) resource;

    // simple parameter
    ParametersParameter p1 = ps.getParameter().get(0);
    assertEquals("exact", p1.getName());
    assertEquals(Boolean.TRUE, p1.getValueBoolean());

    // multipart
    ParametersParameter p2 = ps.getParameter().get(1);
    assertEquals("property", p2.getName());
    assertEquals("code", p2.getPart().get(0).getName());
    assertEquals("focus", p2.getPart().get(0).getValueCode());
    assertEquals("value", p2.getPart().get(1).getName());
    assertEquals("top", p2.getPart().get(1).getValueCode());

    // resource
    ParametersParameter p3 = ps.getParameter().get(2);
    assertEquals("patient", p3.getName());
    assertTrue(p3.getResource() instanceof Patient);
    Patient patient = (Patient)p3.getResource();
    assertEquals("Chalmers", patient.getName().get(0).getFamily());
  }

  @Test
  public void testCodeSystemSerialization() throws IOException {
    String refJson = TestUtils.readResource(PARAMETERS_EXAMPLE_RESOURCE);
    Resource resource = FhirMapper.getObjectMapper().readValue(refJson, Resource.class);
    String json = FhirMapper.toJson(resource);

    JsonAssert.assertJsonEquals(refJson, json);
  }

}
