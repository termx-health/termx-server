package com.kodality.termx.auth;

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
  private PrivilegeResourceActions actions;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class PrivilegeResourceActions {
    private boolean view;
    private boolean edit;
    private boolean publish;
  }
}
