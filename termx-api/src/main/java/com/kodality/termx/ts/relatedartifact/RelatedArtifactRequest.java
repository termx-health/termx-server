package com.kodality.termx.ts.relatedartifact;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelatedArtifactRequest {
  @NotNull
  private String id;
  @NotNull
  private String type;
}
