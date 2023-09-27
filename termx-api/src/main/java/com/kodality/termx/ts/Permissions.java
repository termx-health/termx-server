package com.kodality.termx.ts;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Permissions {
  private String admin;
  private String editor;
  private String viewer;
  private String endorser;
}
