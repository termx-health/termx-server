package com.kodality.termserver.ts.namingsystem;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class NamingSystemIdentifier {
  private String type;
  private String value;
  private boolean preferred;
}
