package com.kodality.zmei.fhir.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import com.kodality.zmei.fhir.FhirMapper;
import java.io.IOException;
import org.junit.Assert;

public class JsonAssert {
  private JsonAssert() {}

  public static void assertJsonEquals(String expected, String actual) {
    ObjectMapper om = FhirMapper.getObjectMapper();
    JsonNode expectedJson = parse(om, expected);
    JsonNode actualJson = parse(om, actual);
    JsonNode patch = JsonDiff.asJson(expectedJson, actualJson);
    if (patch.size() != 0) {
      String message = String.format(
          "Expected:\n%s\nBut got:\n%s\nPatch:\n%s",
          prettyPrint(om, expectedJson),
          prettyPrint(om, actualJson),
          prettyPrint(om, patch)
      );
      Assert.fail(message);
    }
  }

  private static String prettyPrint(ObjectMapper om, JsonNode node) {
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    } catch (IOException e) {
      throw new AssertionError(String.format("Failed to foramt json:%s\n%s\n", e.getMessage(), node));
    }
  }

  private static JsonNode parse(ObjectMapper om, String json) {
    try {
      return om.readTree(json);
    } catch (IOException e) {
      throw new AssertionError(String.format("Failed to parse json:%s\n%s\n", e.getMessage(), json));
    }
  }
}
