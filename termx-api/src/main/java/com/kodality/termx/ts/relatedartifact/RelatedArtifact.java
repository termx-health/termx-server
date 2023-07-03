package com.kodality.termx.ts.relatedartifact;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class RelatedArtifact {
  private String type;
  private String id;
}
