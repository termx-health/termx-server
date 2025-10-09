package com.kodality.termx.ts.mapset;

import io.micronaut.core.annotation.Introspected;
import java.util.List;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class MapSetTransactionRequest {
  @Valid
  private MapSet mapSet;
  @Valid
  private MapSetVersion version;
  private List<MapSetProperty> properties;
  private List<MapSetAssociation> associations;
}
