package com.kodality.zmei.fhir.datatypes;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ReferenceTest {

  @Test
  public void extractTypeFromReference() {
    Assert.assertEquals("Patient", new Reference("Patient/1234").extractTypeFromReference());
    Assert.assertEquals("Patient", new Reference("https://some_server/Patient/1234").extractTypeFromReference());
    assertEquals("Organization", new Reference("Organization/abcd").extractTypeFromReference());
    assertNull(new Reference("5678").extractTypeFromReference());
    assertNull(new Reference(new Identifier()).extractTypeFromReference());
  }

  @Test
  public void extractIdFromReference() {
    assertEquals("1234", new Reference("Patient/1234").extractIdFromReference());
    assertEquals("1234", new Reference("https://some_server/Patient/1234").extractIdFromReference());
    assertEquals("abcd", new Reference("Organization/abcd").extractIdFromReference());
    assertEquals("5678", new Reference("5678").extractIdFromReference());
    assertNull(new Reference(new Identifier()).extractIdFromReference());
  }

  @Test
  public void extractContainedIdFromReference() {
    assertEquals("1234", new Reference("1234").extractContainedIdFromReference());
    assertEquals("1234", new Reference("Patient/1234").extractContainedIdFromReference());
    assertEquals("1234", new Reference("https://some_server/Patient/1234").extractContainedIdFromReference());
    assertEquals("1234", new Reference("#Patient/1234").extractContainedIdFromReference());
    assertEquals("1234", new Reference("#1234").extractContainedIdFromReference());
    assertNull(new Reference(new Identifier()).extractContainedIdFromReference());
  }
}
