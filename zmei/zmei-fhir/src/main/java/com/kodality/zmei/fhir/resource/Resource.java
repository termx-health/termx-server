package com.kodality.zmei.fhir.resource;

import com.kodality.zmei.fhir.Any;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Resource extends Any {
  private final String resourceType;
  private String id;
  private Meta meta;
  private String implicitRules;
  private String language;

  public Resource(String resourceType) {
    this.resourceType = resourceType;
  }
}
