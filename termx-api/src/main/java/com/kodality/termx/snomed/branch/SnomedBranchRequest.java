package com.kodality.termx.snomed.branch;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SnomedBranchRequest {
  private String parent;
  private String name;
  private Object metadata;
}
