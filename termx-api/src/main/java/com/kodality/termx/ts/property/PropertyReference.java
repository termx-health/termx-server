package com.kodality.termx.ts.property;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PropertyReference {
  private Long id;
  private String name;
  private String type;
  private String kind; // designation, property
  private String uri;
}
