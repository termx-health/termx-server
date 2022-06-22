package com.kodality.termserver.auth;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class PrivilegeResource {
  private Long id;
  private String resourceType;
  private String resourceId;
}
