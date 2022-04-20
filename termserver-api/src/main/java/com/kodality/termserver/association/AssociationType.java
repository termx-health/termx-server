package com.kodality.termserver.association;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssociationType {
  private String code;
  private String associationKind;
  private String forwardName;
  private String reverseName;
  private boolean directed;
  private String description;
}
