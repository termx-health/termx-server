package com.kodality.zmei.fhir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodality.zmei.fhir.jackson.ZmeiModule;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.entities.Organization;
import com.kodality.zmei.fhir.resource.individual.Patient;
import com.kodality.zmei.fhir.resource.individual.Practitioner;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.support.Coverage;
import com.kodality.zmei.fhir.resource.workflow.Schedule;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ResourceDeserializerTest {
  private static ObjectMapper mapper;

  @Before
  public void setup() {
    mapper = new ObjectMapper();
    mapper.registerModule(new ZmeiModule());
  }

  @Test(expected = IllegalArgumentException.class)
  public void noType() {
    fromJson("{\"a\":1}", Resource.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void unknownType() {
    fromJson("{\"resourceType\":\"BONOBONOB\"}", Resource.class);
  }

  @Test
  public void schedule() {
    Assert.assertTrue(fromJson("{\"resourceType\":\"Schedule\"}", Schedule.class) instanceof Schedule);
    Assert.assertTrue(fromJson("{\"resourceType\":\"Schedule\"}", Resource.class) instanceof Schedule);
    Schedule sch = fromJson("{\"resourceType\":\"Schedule\", \"comment\":\"hello\"}", Resource.class);
    Assert.assertEquals("hello", sch.getComment());
  }

  @Test
  public void organization() {
    Assert.assertTrue(fromJson("{\"resourceType\":\"Organization\"}", Organization.class) instanceof Organization);
    Assert.assertTrue(fromJson("{\"resourceType\":\"Organization\"}", Resource.class) instanceof Organization);
    Organization organization = fromJson("{\"resourceType\":\"Organization\", \"name\":\"MI6\"}", Resource.class);
    Assert.assertEquals("MI6", organization.getName());
  }

  @Test
  public void practitioner() {
    Assert.assertTrue(fromJson("{\"resourceType\":\"Practitioner\"}", Practitioner.class) instanceof Practitioner);
    Assert.assertTrue(fromJson("{\"resourceType\":\"Practitioner\"}", Resource.class) instanceof Practitioner);
    Practitioner practitioner = fromJson("{\"resourceType\":\"Practitioner\", \"id\":\"KAA\"}", Resource.class);
    Assert.assertEquals("KAA", practitioner.getId());
  }


  @Test
  public void coverage() {
    Assert.assertTrue(fromJson("{\"resourceType\":\"Coverage\"}", Coverage.class) instanceof Coverage);
    Assert.assertTrue(fromJson("{\"resourceType\":\"Coverage\"}", Resource.class) instanceof Coverage);
    Coverage coverage = fromJson("{\"resourceType\":\"Coverage\", \"id\":\"Benny\"}", Resource.class);
    Assert.assertEquals("Benny", coverage.getId());
  }

  @Test
  public void operationOutcome() {
    Assert.assertTrue(fromJson("{\"resourceType\":\"OperationOutcome\"}", OperationOutcome.class) instanceof OperationOutcome);
    Assert.assertTrue(fromJson("{\"resourceType\":\"OperationOutcome\"}", Resource.class) instanceof OperationOutcome);
    OperationOutcome outcome = fromJson("{\"resourceType\":\"OperationOutcome\", \"issue\":[{\"code\":\"some\"}]}", Resource.class);
    Assert.assertEquals("some", outcome.getIssue().get(0).getCode());
  }

  @Test
  public void patient() {
    Assert.assertTrue(fromJson("{\"resourceType\":\"Patient\"}", Patient.class) instanceof Patient);
    Assert.assertTrue(fromJson("{\"resourceType\":\"Patient\"}", Resource.class) instanceof Patient);
    Patient patient = fromJson("{\"resourceType\":\"Patient\", \"id\":\"SeriousSam\"}", Patient.class);
    Assert.assertEquals("SeriousSam", patient.getId());
  }

  @Test
  public void bundle() {
    Assert.assertTrue(fromJson("{\"resourceType\":\"Bundle\"}", Bundle.class) instanceof Bundle);
    Assert.assertTrue(fromJson("{\"resourceType\":\"Bundle\"}", Resource.class) instanceof Bundle);

    Bundle bundle = fromJson("{\"resourceType\":\"Bundle\", \"entry\":[" +
        "{\"resource\": {\"resourceType\": \"Schedule\", \"comment\":\"sched1\"}}," +
        "{\"resource\": {\"resourceType\": \"Schedule\", \"comment\":\"sched2\"}}" +
        "]}", Resource.class);
    Assert.assertEquals(2, bundle.getEntry().size());
    Assert.assertTrue(bundle.getEntry().get(0).getResource() instanceof Schedule);
    Assert.assertEquals("sched1", bundle.getEntry().get(0).<Schedule>getResource().getComment());
    Assert.assertTrue(bundle.getEntry().get(1).getResource() instanceof Schedule);
    Assert.assertEquals("sched2", bundle.getEntry().get(1).<Schedule>getResource().getComment());
  }

  @Test
  public void dateParse() {
    String json = "{\"resourceType\":\"Patient\",\"deceasedDateTime\":\"2001-02-03T04:05:06+04:00\"}";
    Patient patient = FhirMapper.fromJson(json, Patient.class);
    Assert.assertEquals(OffsetDateTime.of(2001, 2, 3, 4, 5, 6, 0, ZoneOffset.ofHours(4)), patient.getDeceasedDateTime());
    Assert.assertEquals(json, FhirMapper.toJson(patient));
  }


  public static <T> T fromJson(String json, Class<?> cls) {
    try {
      return (T) mapper.readValue(json, cls);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("error parsing json: " + e.getMessage());
    }
  }
}
