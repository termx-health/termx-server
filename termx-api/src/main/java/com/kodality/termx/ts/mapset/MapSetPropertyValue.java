package com.kodality.termx.ts.mapset;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class MapSetPropertyValue {
  private Long id;
  private Object value;
  private Long mapSetPropertyId;
  private Long mapSetAssociationId;

  private String mapSetPropertyName;
  private String mapSetPropertyType;
}
