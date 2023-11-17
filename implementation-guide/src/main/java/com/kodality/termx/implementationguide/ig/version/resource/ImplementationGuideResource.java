package com.kodality.termx.implementationguide.ig.version.resource;

import com.kodality.termx.implementationguide.ig.version.group.ImplementationGuideGroup;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuideResource {
  private Long id;
  private String type;
  private String reference;
  private String version;
  private String name;
  private ImplementationGuideGroup group;
}
