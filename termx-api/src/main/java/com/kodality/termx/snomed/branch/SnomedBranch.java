package com.kodality.termx.snomed.branch;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SnomedBranch {
  private String path;
  private String state;
  private Boolean containsContent;
  private Boolean locked;
  private OffsetDateTime creation;
  private OffsetDateTime base;
  private OffsetDateTime head;
  private Boolean deleted;
  private Object metadata;
}
