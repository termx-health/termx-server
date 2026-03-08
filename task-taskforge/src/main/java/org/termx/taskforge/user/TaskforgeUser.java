package org.termx.taskforge.user;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TaskforgeUser {
  @NotNull
  private String sub;
  private String name;
}
