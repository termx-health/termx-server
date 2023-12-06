package com.kodality.termx.core.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class User {
  private String sub;
  private String name;
  @JsonIgnore
  private Set<String> privileges;
}
