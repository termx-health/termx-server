package com.kodality.zmei.fhir.search;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FhirQueryParamsTest {

  @Test
  public void testGetOffset() {
    FhirQueryParams params = new FhirQueryParams();
    params.put(FhirQueryParams.count, List.of("10"));
    params.put(FhirQueryParams.page, List.of("1"));
    assertEquals(0, params.getOffset().intValue());
    
    params.put(FhirQueryParams.page, List.of("3"));
    assertEquals(20, params.getOffset().intValue());
  }
  
  @Test
  public void testSetOffset() {
    FhirQueryParams params = new FhirQueryParams();
    params.put(FhirQueryParams.count, List.of("10"));
    params.setOffset(0);
    assertEquals(1, params.getPage().intValue());
    
    params.setOffset(20);
    assertEquals(3, params.getPage().intValue());
  }

}
