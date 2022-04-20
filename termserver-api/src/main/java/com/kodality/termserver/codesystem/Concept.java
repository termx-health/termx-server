package com.kodality.termserver.codesystem;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Concept extends CodeSystemEntity {
  private String code;
  private String description;
}
