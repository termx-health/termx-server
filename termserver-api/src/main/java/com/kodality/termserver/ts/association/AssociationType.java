package com.kodality.termserver.ts.association;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
@NoArgsConstructor
public class AssociationType {
  private String code;
  private String associationKind;
  private String forwardName;
  private String reverseName;
  private boolean directed;
  private String description;

  public AssociationType(String code, String associationKind, boolean directed) {
    this.code = code;
    this.associationKind = associationKind;
    this.directed = directed;
  }
}
