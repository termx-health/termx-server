package com.kodality.termserver.codesystem;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EntityPropertyValue {
  private Long id;
  private String value;
  private Long entityPropertyId;
}
