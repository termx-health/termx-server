package com.kodality.termserver.mapset;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class MapSetTransactionRequest {
  private MapSet mapSet;
  private MapSetVersion version;
  private List<MapSetAssociation> associations;
}
