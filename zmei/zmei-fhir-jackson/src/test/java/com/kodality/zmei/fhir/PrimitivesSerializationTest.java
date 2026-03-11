package com.kodality.zmei.fhir;

import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class PrimitivesSerializationTest {

  @Test
  public void serializePrimitiveExtension() {
    CodeSystem cs = new CodeSystem();
    cs.setTitle("title text");
    cs.addPrimitiveExtension("title", new Extension("urn:test-extension").setValueString("extension value"));
    Assert.assertEquals(
        "{\"resourceType\":\"CodeSystem\",\"title\":\"title text\",\"_title\":{\"extension\":[{\"url\":\"urn:test-extension\",\"valueString\":\"extension value\"}]}}",
        FhirMapper.toJson(cs));
  }

  @Test
  public void deserializePrimitiveExtension() {
    String json =
        "{\"resourceType\":\"CodeSystem\",\"title\":\"title text\",\"_title\":{\"extension\":[{\"url\":\"urn:test-extension\",\"valueString\":\"extension value\"}]}}";
    CodeSystem cs = FhirMapper.fromJson(json, CodeSystem.class);
    Assert.assertEquals("title text", cs.getTitle());
    Assert.assertTrue(cs.getPrimitiveElement("title") != null && cs.getPrimitiveElement("title").getExtension("urn:test-extension").isPresent());
    Assert.assertEquals("extension value", cs.getPrimitiveElement("title").getExtension("urn:test-extension").orElseThrow().getValueString());
  }

  @Test
  public void unknownFieldShouldNotFail() {
    String json = "{\"resourceType\":\"CodeSystem\",\"title\":\"title text\",\"title:en\":\"some text\"}";
    CodeSystem cs = FhirMapper.fromJson(json, CodeSystem.class);
    Assert.assertEquals("title text", cs.getTitle());
    Assert.assertTrue(cs.getPrimitiveElement("title") == null);
  }

  @Test
  public void primitiveExtensionFieldIsAfterPrimitiveField() {
    CodeSystem cs = new CodeSystem();
    cs.setTitle("title text");
    cs.addPrimitiveExtension("title", new Extension("asd").setValueString("sdf"));
    cs.setDescription("desc");
    cs.setPrimitiveExtensions("purpose", List.of(new Extension("22").setValueString("qwe")));
    cs.setAuthor(List.of(new ContactDetail().setName("author").<ContactDetail>addPrimitiveExtension("name", new Extension("author-ext"))));
    String json = FhirMapper.toJson(cs);
    System.out.println(json);
    Assert.assertTrue(json.contains("\"title\":\"title text\",\"_title\":{"));
    Assert.assertTrue(json.contains("\"name\":\"author\",\"_name\":"));
  }


}
