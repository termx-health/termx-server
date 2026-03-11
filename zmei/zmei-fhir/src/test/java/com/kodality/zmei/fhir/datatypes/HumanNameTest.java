package com.kodality.zmei.fhir.datatypes;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HumanNameTest {

  @Test
  public void formattedName() {
    HumanName name = new HumanName().setGiven(List.of("Ivan")).setFamily("Petrov");
    assertEquals("Petrov, Ivan", name.formattedName());
  }

  @Test
  public void formattedFamilyName() {
    HumanName name = new HumanName().setFamily("Petrov");
    assertEquals("Petrov", name.formattedName());
  }
}
