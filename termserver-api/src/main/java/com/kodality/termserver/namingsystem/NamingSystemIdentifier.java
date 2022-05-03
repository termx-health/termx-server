package com.kodality.termserver.namingsystem;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NamingSystemIdentifier {
  private String type;
  private String value;
  private boolean preferred;
}
