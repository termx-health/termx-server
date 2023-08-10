package com.kodality.termx.bob;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class BobStorage {
  private Long id;
  private String storageType;
  private String container;
  private String filename;
  private String path;

  public String getFullPath() {
    return path + filename;
  }
}
