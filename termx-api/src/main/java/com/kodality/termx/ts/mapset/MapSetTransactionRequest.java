package com.kodality.termx.ts.mapset;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class MapSetTransactionRequest {
  private MapSet mapSet;
  private MapSetVersion version;
  private List<MapSetProperty> properties;
  private List<MapSetAssociation> associations;
}
