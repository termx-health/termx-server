package com.kodality.termx.implementationguide.ig.version.group;

import com.kodality.commons.model.LocalizedName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuideGroup {
  private Long id;
  private String name;
  private LocalizedName description;
}
