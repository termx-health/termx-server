package com.kodality.termserver.fhir.codesystem;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FhirTestCase {
  private String introduction;
  private List<FhirTestSuite> suites;

  @Getter
  @Setter
  public static class FhirTestSuite {
    private String name;
    private String description;
    private List<String> setup;
    private List<FhirTest> tests;
  }

  @Getter
  @Setter
  public static class FhirTest {
    private String name;
    private String operation;
    private String request;
    private String response;
  }
}
