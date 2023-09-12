package com.kodality.termx.ts.mapset;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class MapSetAssociation {
  private Long id;
  private String mapSet;
  private MapSetVersionReference mapSetVersion;

  private MapSetAssociationEntity source;
  private MapSetAssociationEntity target;
  private String relationship;
  private boolean verified;
  private boolean noMap; //calculated field (true if target code is null)

  private List<MapSetPropertyValue> propertyValues;

  @Getter
  @Setter
  public static class MapSetAssociationEntity {
    private String code;
    private String codeSystem;
    private String display;

    private String codeSystemUri; //only loaded field
  }
}
