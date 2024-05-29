package com.kodality.termx.sys;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ResourceReference {
  private String resourceType;
  private String resourceId;
  private String resourceVersion;
}
