package com.kodality.termx.ts;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ConfigurationAttribute {
  private String attribute;
  private String value;
  private String language;
}
