package com.kodality.termserver;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;

@OpenAPIDefinition
public class TerminologyServerApplication {

  public static void main(String[] args) {
    Micronaut.run(TerminologyServerApplication.class, args);
  }
}

