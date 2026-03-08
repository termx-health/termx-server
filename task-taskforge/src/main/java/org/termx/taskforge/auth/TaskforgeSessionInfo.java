package org.termx.taskforge.auth;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TaskforgeSessionInfo {
  private String sub;
  private String tenant;
}
