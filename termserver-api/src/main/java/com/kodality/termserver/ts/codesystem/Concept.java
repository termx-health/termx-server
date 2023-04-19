package com.kodality.termserver.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class Concept extends CodeSystemEntity {
  private String code;
  private String description;

  private Boolean leaf;
  private Long childCount;
}
