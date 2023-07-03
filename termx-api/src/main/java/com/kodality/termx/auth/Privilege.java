package com.kodality.termx.auth;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class Privilege {
  private Long id;
  private String code;
  private LocalizedName names;

  private List<PrivilegeResource> resources;
}
