package com.kodality.termserver.association;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class AssociationType {
  private String code;
  private String associationKind;
  private String forwardName;
  private String reverseName;
  private boolean directed;
  private String description;
}
